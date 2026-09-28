package com.pulso.backend.preference.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.domain.OnboardingPreferences;

@Repository
public class JdbcOnboardingPreferencesRepository implements OnboardingPreferencesRepository {

    private static final String PREFERENCE_COLUMNS = """
            profile_id,
            intentions::text as intentions,
            preferred_sectors::text as preferred_sectors,
            preferred_stages::text as preferred_stages
            """;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
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
                from public.profile_preferences
                where profile_id = :profileId
                """.formatted(PREFERENCE_COLUMNS))
                .param("profileId", profileId)
                .query(this::mapPreferences)
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public OnboardingPreferences upsert(OnboardingPreferences preferences) {
        return jdbcClient.sql("""
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
                returning %s
                """.formatted(PREFERENCE_COLUMNS))
                .param("profileId", preferences.profileId())
                .param("intentions", toJson(preferences.intentions()))
                .param("preferredSectors", toJson(preferences.preferredSectors()))
                .param("preferredStages", toJson(preferences.preferredStages()))
                .query(this::mapPreferences)
                .single();
    }

    private OnboardingPreferences mapPreferences(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new OnboardingPreferences(
                resultSet.getObject("profile_id", UUID.class),
                fromJson(resultSet, "intentions"),
                fromJson(resultSet, "preferred_sectors"),
                fromJson(resultSet, "preferred_stages"),
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

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    ApiMessages.PREFERENCES_SERIALIZE_FAILED,
                    exception);
        }
    }
}
