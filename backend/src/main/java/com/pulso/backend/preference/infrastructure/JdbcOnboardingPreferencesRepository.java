package com.pulso.backend.preference.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.ProfileSkill;

@Repository
public class JdbcOnboardingPreferencesRepository implements OnboardingPreferencesRepository {

    private static final String PREFERENCE_COLUMNS = """
            profiles.id as profile_id,
            profiles.onboarding_completed_at,
            coalesce(profile_preferences.intentions, '[]'::jsonb)::text as intentions,
            coalesce(profile_preferences.preferred_sectors, '[]'::jsonb)::text as preferred_sectors,
            coalesce(profile_preferences.preferred_stages, '[]'::jsonb)::text as preferred_stages,
            coalesce(profile_preferences.recommendation_weights, '{}'::jsonb)::text as recommendation_weights,
            coalesce((
                select jsonb_agg(profile_tags.tag_id order by profile_tags.tag_id)
                from public.profile_tags
                where profile_tags.profile_id = profiles.id
            ), '[]'::jsonb)::text as preferred_tags,
            coalesce((
                select jsonb_agg(jsonb_build_object(
                    'skillId', profile_skills.skill_id,
                    'proficiency', profile_skills.proficiency
                ) order by profile_skills.skill_id)
                from public.profile_skills
                where profile_skills.profile_id = profiles.id
            ), '[]'::jsonb)::text as skills
            """;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileSkill>> SKILLS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Double>> WEIGHTS_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final PreferenceProperties properties;

    public JdbcOnboardingPreferencesRepository(
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            PreferenceProperties properties) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
        return jdbcClient.sql("""
                select %s
                from public.profiles
                left join public.profile_preferences on profile_preferences.profile_id = profiles.id
                where profiles.id = :profileId
                """.formatted(PREFERENCE_COLUMNS))
                .param("profileId", profileId)
                .query(this::mapPreferences)
                .list()
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public OnboardingPreferences upsert(OnboardingPreferences preferences) {
        return upsert(preferences, false);
    }

    @Override
    @Transactional
    public OnboardingPreferences upsert(OnboardingPreferences preferences, boolean completeOnboarding) {
        jdbcClient.sql("""
                insert into public.profile_preferences (
                    profile_id,
                    intentions,
                    preferred_sectors,
                    preferred_stages
                ) values (
                    :profileId,
                    cast(:intentions as jsonb),
                    cast(:preferredSectors as jsonb),
                    cast(:preferredStages as jsonb)
                )
                on conflict (profile_id) do update
                set intentions = excluded.intentions,
                    preferred_sectors = excluded.preferred_sectors,
                    preferred_stages = excluded.preferred_stages
                """)
                .param("profileId", preferences.profileId())
                .param("intentions", toJson(preferences.intentions()))
                .param("preferredSectors", toJson(preferences.preferredSectors()))
                .param("preferredStages", toJson(preferences.preferredStages()))
                .update();

        replaceProfileTags(preferences);
        replaceProfileSkills(preferences);
        if (completeOnboarding) {
            jdbcClient.sql("""
                    update public.profiles
                    set onboarding_completed_at = current_timestamp
                    where id = :profileId and onboarding_completed_at is null
                    """)
                    .param("profileId", preferences.profileId())
                    .update();
        }
        return findByProfileId(preferences.profileId()).orElseThrow();
    }

    @Override
    @Transactional
    public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
        jdbcClient.sql("""
                insert into public.profile_preferences (profile_id, recommendation_weights)
                values (:profileId, cast(:weights as jsonb))
                on conflict (profile_id) do update
                set recommendation_weights = excluded.recommendation_weights
                """)
                .param("profileId", profileId)
                .param("weights", toJson(weights))
                .update();
    }

    private OnboardingPreferences mapPreferences(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new OnboardingPreferences(
                resultSet.getObject("profile_id", UUID.class),
                fromJson(resultSet, "intentions"),
                fromJson(resultSet, "preferred_sectors"),
                fromJson(resultSet, "preferred_stages"),
                fromJson(resultSet, "preferred_tags"),
                fromSkills(resultSet),
                fromWeights(resultSet, "recommendation_weights"),
                resultSet.getObject("onboarding_completed_at", OffsetDateTime.class),
                new OnboardingPreferences.Limits(
                        properties.maxItemsPerField(),
                        properties.maxTokenLength(),
                        properties.sectorIdMaxLength()));
    }

    private List<String> fromJson(ResultSet resultSet, String column) throws SQLException {
        try {
            return objectMapper.readValue(
                    resultSet.getString(column),
                    STRING_LIST_TYPE);
        } catch (JacksonException exception) {
            throw new SQLException(
                    ApiMessages.STORED_PREFERENCES_INVALID_JSON,
                    exception);
        }
    }

    private String toJson(Object values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    ApiMessages.PREFERENCES_SERIALIZE_FAILED,
                    exception);
        }
    }

    private Map<String, Double> fromWeights(ResultSet resultSet, String column) throws SQLException {
        try {
            return objectMapper.readValue(
                    resultSet.getString(column),
                    WEIGHTS_TYPE);
        } catch (JacksonException exception) {
            throw new SQLException(
                    ApiMessages.STORED_PREFERENCES_INVALID_JSON,
                    exception);
        }
    }

    private void replaceProfileTags(OnboardingPreferences preferences) {
        jdbcClient.sql("delete from public.profile_tags where profile_id = :profileId")
                .param("profileId", preferences.profileId())
                .update();
        for (String tagId : preferences.preferredTags()) {
            jdbcClient.sql("""
                    insert into public.profile_tags (profile_id, tag_id)
                    values (:profileId, :tagId)
                    """)
                    .param("profileId", preferences.profileId())
                    .param("tagId", UUID.fromString(tagId))
                    .update();
        }
    }

    private List<ProfileSkill> fromSkills(ResultSet resultSet) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString("skills"), SKILLS_TYPE);
        } catch (JacksonException exception) {
            throw new SQLException(ApiMessages.STORED_PREFERENCES_INVALID_JSON, exception);
        }
    }

    private void replaceProfileSkills(OnboardingPreferences preferences) {
        if (preferences.skills().isEmpty()) {
            jdbcClient.sql("delete from public.profile_skills where profile_id = :profileId")
                    .param("profileId", preferences.profileId())
                    .update();
            return;
        }
        jdbcClient.sql("""
                delete from public.profile_skills
                where profile_id = :profileId and skill_id not in (:skillIds)
                """)
                .param("profileId", preferences.profileId())
                .param("skillIds", preferences.skills().stream()
                        .map(skill -> UUID.fromString(skill.skillId())).toList())
                .update();
        for (ProfileSkill skill : preferences.skills()) {
            jdbcClient.sql("""
                    insert into public.profile_skills (profile_id, skill_id, proficiency)
                    values (:profileId, :skillId, :proficiency)
                    on conflict (profile_id, skill_id) do update
                    set proficiency = coalesce(excluded.proficiency, profile_skills.proficiency)
                    """)
                    .param("profileId", preferences.profileId())
                    .param("skillId", UUID.fromString(skill.skillId()))
                    .param("proficiency", skill.proficiency())
                    .update();
        }
    }
}
