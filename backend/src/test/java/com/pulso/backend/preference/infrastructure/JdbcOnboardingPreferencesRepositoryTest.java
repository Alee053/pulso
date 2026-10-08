package com.pulso.backend.preference.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.preference.domain.OnboardingPreferences;

@Testcontainers(disabledWithoutDocker = true)
class JdbcOnboardingPreferencesRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SECTOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TAG_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SKILL_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID INACTIVE_TAG_ID = UUID.fromString(
            "dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID INACTIVE_SKILL_ID = UUID.fromString(
            "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final PreferenceProperties PROPERTIES =
            new PreferenceProperties(10, 40, 120);
    private static final OnboardingPreferences.Limits LIMITS =
            new OnboardingPreferences.Limits(10, 40, 120);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcOnboardingPreferencesRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcOnboardingPreferencesRepository(jdbcClient, new ObjectMapper(), PROPERTIES);

        jdbcClient.sql("drop table if exists public.profile_preferences, public.profile_tags, public.profile_skills, public.tags, public.skills, public.sectors, public.profiles cascade")
                .update();
        jdbcClient.sql("""
                create table public.profiles (
                  id uuid primary key
                )
                """).update();
        jdbcClient.sql("""
                create table public.sectors (
                  id uuid primary key,
                  slug text not null,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.tags (
                  id uuid primary key,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.skills (
                  id uuid primary key,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.profile_preferences (
                  profile_id uuid primary key references public.profiles (id),
                  intentions jsonb not null default '[]'::jsonb,
                  preferred_sectors jsonb not null default '[]'::jsonb,
                  preferred_stages jsonb not null default '[]'::jsonb,
                  recommendation_weights jsonb not null default '{}'::jsonb,
                  updated_at timestamptz not null default timezone('utc', now())
                )
                """).update();
        jdbcClient.sql("""
                create table public.profile_tags (
                  profile_id uuid not null references public.profiles (id),
                  tag_id uuid not null references public.tags (id),
                  primary key (profile_id, tag_id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.profile_skills (
                  profile_id uuid not null references public.profiles (id),
                  skill_id uuid not null references public.skills (id),
                  primary key (profile_id, skill_id)
                )
                """).update();
        jdbcClient.sql("insert into public.profiles (id) values (:profileId)")
                .param("profileId", PROFILE_ID)
                .update();
        jdbcClient.sql("insert into public.sectors (id, slug, is_active) values (:id, :slug, true)")
                .param("id", SECTOR_ID)
                .param("slug", "sostenibilidad")
                .update();
        jdbcClient.sql("insert into public.tags (id, is_active) values (:id, true)")
                .param("id", TAG_ID)
                .update();
        jdbcClient.sql("insert into public.tags (id, is_active) values (:id, false)")
                .param("id", INACTIVE_TAG_ID)
                .update();
        jdbcClient.sql("insert into public.skills (id, is_active) values (:id, true)")
                .param("id", SKILL_ID)
                .update();
        jdbcClient.sql("insert into public.skills (id, is_active) values (:id, false)")
                .param("id", INACTIVE_SKILL_ID)
                .update();
    }

    @Test
    void returnsNoPreferencesBeforeTheFirstSave() {
        assertThat(repository.findByProfileId(PROFILE_ID)).isEmpty();
    }

    @Test
    void looksUpOnlyActivePreferenceCatalogIds() {
        JdbcActivePreferenceCatalogLookup lookup = new JdbcActivePreferenceCatalogLookup(jdbcClient);

        assertThat(lookup.findActiveTagIds(List.of(TAG_ID, INACTIVE_TAG_ID)))
                .containsExactly(TAG_ID);
        assertThat(lookup.findActiveSkillIds(List.of(SKILL_ID, INACTIVE_SKILL_ID)))
                .containsExactly(SKILL_ID);
    }

    @Test
    void savesJsonbPreferencesAndUpsertsTheSameProfile() {
        OnboardingPreferences first = preferences(
                List.of("DISCOVER"),
                List.of("sostenibilidad"),
                List.of("MVP"));
        OnboardingPreferences second = preferences(
                List.of("DISCOVER", "COLLABORATE"),
                List.of(SECTOR_ID.toString()),
                List.of("EARLY_STAGE"));

        repository.upsert(first);
        OnboardingPreferences saved = repository.upsert(second);

        assertThat(saved.intentions()).containsExactly("DISCOVER", "COLLABORATE");
        assertThat(repository.findByProfileId(PROFILE_ID)).contains(second);
        assertThat(countPreferences()).isEqualTo(1);
    }

    @Test
    void persistsCatalogSelectionsAndRetainsLearnedRecommendationWeights() {
        OnboardingPreferences initial = new OnboardingPreferences(
                PROFILE_ID,
                List.of("DISCOVER"),
                List.of("sostenibilidad"),
                List.of("MVP"),
                List.of(TAG_ID.toString()),
                List.of(SKILL_ID.toString()),
                Map.of(),
                LIMITS);
        repository.upsert(initial);
        assertThat(countPreferences()).isEqualTo(1);
        repository.saveRecommendationWeights(PROFILE_ID, Map.of("sostenibilidad", 2.5));

        OnboardingPreferences updated = new OnboardingPreferences(
                PROFILE_ID,
                List.of("COLLABORATE"),
                List.of(),
                List.of("EARLY_STAGE"),
                List.of(TAG_ID.toString()),
                List.of(SKILL_ID.toString()),
                Map.of(),
                LIMITS);
        OnboardingPreferences saved = repository.upsert(updated);

        assertThat(saved.intentions()).containsExactly("COLLABORATE");
        assertThat(saved.preferredTags()).containsExactly(TAG_ID.toString());
        assertThat(saved.preferredSkills()).containsExactly(SKILL_ID.toString());
        assertThat(saved.recommendationWeights()).containsEntry("sostenibilidad", 2.5);
    }

    @Test
    void surfacesDatasourceFailuresAsSpringDataAccessExceptions() {
        DataSource unavailableDataSource = new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:1/postgres?connectTimeout=1",
                "postgres",
                "postgres");
        JdbcOnboardingPreferencesRepository unavailableRepository =
                new JdbcOnboardingPreferencesRepository(
                        JdbcClient.create(unavailableDataSource),
                        new ObjectMapper(),
                        PROPERTIES);

        assertThatThrownBy(() -> unavailableRepository.findByProfileId(PROFILE_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> unavailableRepository.upsert(
                preferences(List.of("DISCOVER"), List.of(), List.of("MVP"))))
                .isInstanceOf(DataAccessException.class);
    }

    private int countPreferences() {
        return jdbcClient.sql("select count(*) from public.profile_preferences")
                .query((resultSet, rowNumber) -> resultSet.getInt(1))
                .single();
    }

    private static OnboardingPreferences preferences(
            List<String> intentions,
            List<String> sectors,
            List<String> stages) {
        return new OnboardingPreferences(PROFILE_ID, intentions, sectors, stages, LIMITS);
    }
}
