package com.pulso.backend.preference.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.ProfileSkill;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;

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

    private AnnotationConfigApplicationContext context;
    private JdbcClient jdbcClient;
    private OnboardingPreferencesRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(JdbcOnboardingPreferencesRepository.class,
                () -> new JdbcOnboardingPreferencesRepository(jdbcClient, new ObjectMapper(), PROPERTIES));
        context.refresh();
        repository = context.getBean(OnboardingPreferencesRepository.class);

        jdbcClient.sql("drop table if exists public.profile_preferences, public.profile_tags, public.profile_skills, public.tags, public.skills, public.sectors, public.profiles cascade")
                .update();
        jdbcClient.sql("""
                create table public.profiles (
                  id uuid primary key,
                  onboarding_completed_at timestamptz
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
                  proficiency text check (proficiency in ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
                  is_primary boolean not null default false,
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

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void savesSkillLevelsAndCompletesOnboardingOnlyOnce() {
        OnboardingPreferences selection = new OnboardingPreferences(
                PROFILE_ID, List.of("COLLABORATE"), List.of(), List.of(), List.of(TAG_ID.toString()),
                List.of(new ProfileSkill(SKILL_ID.toString(), "ADVANCED")), Map.of(), null, LIMITS);
        OnboardingPreferences first = repository.upsert(selection, true);
        assertThat(first.onboardingCompletedAt()).isNotNull();
        assertThat(first.skills()).containsExactly(new ProfileSkill(SKILL_ID.toString(), "ADVANCED"));

        OnboardingPreferences retry = repository.upsert(selection, true);
        assertThat(retry.onboardingCompletedAt()).isEqualTo(first.onboardingCompletedAt());
        assertThat(retry.skills()).containsExactlyElementsOf(first.skills());
        assertThat(jdbcClient.sql("select count(*) from public.profile_tags").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("select count(*) from public.profile_skills").query(Long.class).single()).isEqualTo(1L);
        assertThat(countPreferences()).isEqualTo(1);
    }

    @Test
    void returnsEmptySelectionsBeforeTheFirstSave() {
        assertThat(repository.findByProfileId(PROFILE_ID)).contains(OnboardingPreferences.empty(PROFILE_ID, LIMITS));
    }

    @Test
    void readsProfileCompletionWithoutAPreferenceRow() {
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-10-09T12:00:00Z");
        jdbcClient.sql("update public.profiles set onboarding_completed_at = :completedAt where id = :profileId")
                .param("completedAt", completedAt).param("profileId", PROFILE_ID).update();
        OnboardingPreferences saved = repository.findByProfileId(PROFILE_ID).orElseThrow();
        assertThat(saved.intentions()).isEmpty();
        assertThat(saved.skills()).isEmpty();
        assertThat(saved.preferredTags()).isEmpty();
        assertThat(saved.recommendationWeights()).isEmpty();
        assertThat(saved.onboardingCompletedAt()).isEqualTo(completedAt);
        assertThat(countPreferences()).isZero();
    }

    @Test
    void returnsNoPreferencesForANonexistentProfile() {
        assertThat(repository.findByProfileId(UUID.fromString("22222222-2222-2222-2222-222222222222"))).isEmpty();
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
    void editsLevelsAndRemovesSelectionsWithoutLosingPrimaryFlagsOrLearnedWeights() {
        OnboardingPreferences first = selection(List.of(TAG_ID.toString()),
                List.of(new ProfileSkill(SKILL_ID.toString(), "ADVANCED")));
        var completedAt = repository.upsert(first, true).onboardingCompletedAt();
        repository.saveRecommendationWeights(PROFILE_ID, Map.of("sostenibilidad", 2.5));
        jdbcClient.sql("update public.profile_skills set is_primary = true where profile_id = :profileId")
                .param("profileId", PROFILE_ID).update();

        OnboardingPreferences legacyEdit = new OnboardingPreferences(
                PROFILE_ID, List.of("COLLABORATE"), List.of(), List.of(), List.of(),
                List.of(SKILL_ID.toString()), Map.of(), LIMITS);
        OnboardingPreferences retained = repository.upsert(legacyEdit);
        assertThat(retained.skills()).containsExactly(new ProfileSkill(SKILL_ID.toString(), "ADVANCED"));
        assertThat(retained.preferredTags()).isEmpty();
        assertThat(retained.onboardingCompletedAt()).isEqualTo(completedAt);
        assertThat(jdbcClient.sql("select is_primary from public.profile_skills where profile_id = :id")
                .param("id", PROFILE_ID).query(Boolean.class).single()).isTrue();

        OnboardingPreferences edited = repository.upsert(selection(List.of(),
                List.of(new ProfileSkill(SKILL_ID.toString(), "EXPERT"))), true);
        assertThat(edited.skills()).containsExactly(new ProfileSkill(SKILL_ID.toString(), "EXPERT"));
        assertThat(edited.onboardingCompletedAt()).isEqualTo(completedAt);
        assertThat(edited.recommendationWeights()).containsEntry("sostenibilidad", 2.5);
        assertThat(jdbcClient.sql("select is_primary from public.profile_skills where profile_id = :id")
                .param("id", PROFILE_ID).query(Boolean.class).single()).isTrue();

        OnboardingPreferences cleared = repository.upsert(selection(List.of(), List.of()), true);
        assertThat(cleared.skills()).isEmpty();
        assertThat(cleared.onboardingCompletedAt()).isEqualTo(completedAt);
        assertThat(cleared.recommendationWeights()).containsEntry("sostenibilidad", 2.5);
    }

    @Test
    void rollsBackAllSelectionsAndCompletionWhenALateSkillWriteFails() {
        OnboardingPreferences original = repository.upsert(selection(List.of(TAG_ID.toString()),
                List.of(new ProfileSkill(SKILL_ID.toString(), "ADVANCED"))));
        jdbcClient.sql("""
                create or replace function public.reject_test_skill_write() returns trigger
                language plpgsql as $$ begin raise exception 'Fixture skill failure'; end; $$
                """).update();
        jdbcClient.sql("""
                create trigger reject_test_skill_write before insert on public.profile_skills
                for each row execute function public.reject_test_skill_write()
                """).update();
        OnboardingPreferences changed = new OnboardingPreferences(
                PROFILE_ID, List.of("INVEST"), List.of(), List.of("MVP"), List.of(),
                List.of(new ProfileSkill(SKILL_ID.toString(), "EXPERT")), Map.of(), null, LIMITS);

        assertThatThrownBy(() -> repository.upsert(changed, true)).isInstanceOf(DataAccessException.class);
        assertThat(repository.findByProfileId(PROFILE_ID)).contains(original);
        assertThat(jdbcClient.sql("select onboarding_completed_at is null from public.profiles where id = :id")
                .param("id", PROFILE_ID).query(Boolean.class).single()).isTrue();
    }

    @Test
    void concurrentRetriesSerializeWholeSelectionsWithoutMixingRelations() throws Exception {
        OnboardingPreferences first = selection(List.of(TAG_ID.toString()),
                List.of(new ProfileSkill(SKILL_ID.toString(), "ADVANCED")));
        OnboardingPreferences second = selection(List.of(), List.of());
        TransactionTemplate transactions = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        try {
            Future<?> firstSave = executor.submit(() -> transactions.executeWithoutResult(status -> {
                repository.upsert(first, true);
                firstSaved.countDown();
                await(commitFirst);
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<OnboardingPreferences> secondSave = executor.submit(() -> repository.upsert(second, true));
            awaitPreferenceLockWaiter();
            commitFirst.countDown();
            firstSave.get(15, TimeUnit.SECONDS);
            OnboardingPreferences result = secondSave.get(15, TimeUnit.SECONDS);
            assertThat(result.skills()).isEmpty();
            assertThat(result.preferredTags()).isEmpty();
            assertThat(result.onboardingCompletedAt()).isNotNull();
            assertThat(repository.findByProfileId(PROFILE_ID)).contains(result);
            assertThat(countPreferences()).isEqualTo(1);
        } finally {
            commitFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitPreferenceLockWaiter() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            long waiters = jdbcClient.sql("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock'
                      and query like '%public.profile_preferences%'
                    """).query(Long.class).single();
            if (waiters > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Concurrent save did not wait for the preference row lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to commit the first save");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Save interrupted", exception);
        }
    }

    private static OnboardingPreferences selection(List<String> tags, List<ProfileSkill> skills) {
        return new OnboardingPreferences(PROFILE_ID, List.of("COLLABORATE"), List.of(), List.of(),
                tags, skills, Map.of(), null, LIMITS);
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

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
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
