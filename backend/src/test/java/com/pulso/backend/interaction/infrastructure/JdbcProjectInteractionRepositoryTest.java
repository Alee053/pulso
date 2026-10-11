package com.pulso.backend.interaction.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.interaction.domain.DiscoveryFeedCursor;
import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.project.application.PublicProjectNotFoundException;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectInteractionRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final UUID SECTOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_SECTOR_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SAVED_PROJECT_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID DISMISSED_PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID AVAILABLE_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID DRAFT_PROJECT_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID BOTH_MATCH_PROJECT_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID STAGE_MATCH_PROJECT_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final UUID NO_MATCH_PROJECT_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888");
    private static final UUID UNKNOWN_PROJECT_ID = UUID.fromString(
            "aaaaaaaa-1111-1111-1111-111111111111");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcProjectInteractionRepository repository;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcProjectInteractionRepository(jdbcClient);
        transactionTemplate = new TransactionTemplate(new JdbcTransactionManager(dataSource));

        jdbcClient.sql("drop table if exists public.interaction_events, public.project_feedback, public.saved_projects, public.projects, public.sectors cascade")
                .update();
        jdbcClient.sql("create table public.sectors (id uuid primary key, slug text not null, name text not null)")
                .update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  tagline text not null,
                  sector_id uuid not null,
                  city text,
                  stage text not null,
                  status text not null,
                  published_at timestamptz,
                  created_at timestamptz not null default timezone('utc', now())
                )
                """).update();
        jdbcClient.sql("""
                create table public.saved_projects (
                  profile_id uuid not null,
                  project_id uuid not null,
                  saved_at timestamptz not null default timezone('utc', now()),
                  unsaved_at timestamptz,
                  primary key (profile_id, project_id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_feedback (
                  profile_id uuid not null,
                  project_id uuid not null,
                  feedback_type text not null,
                  changed_at timestamptz not null default timezone('utc', now()),
                  primary key (profile_id, project_id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.interaction_events (
                  id bigserial primary key,
                  profile_id uuid not null,
                  project_id uuid not null,
                  event_type text not null,
                  source text not null
                )
                """).update();
        jdbcClient.sql("insert into public.sectors (id, slug, name) values (:id, 'sostenibilidad', 'Sostenibilidad')")
                .param("id", SECTOR_ID)
                .update();
        jdbcClient.sql("insert into public.sectors (id, slug, name) values (:id, 'tecnologia', 'Tecnología')")
                .param("id", OTHER_SECTOR_ID)
                .update();
        insertProject(SAVED_PROJECT_ID, "saved-project", "Saved project", "PUBLISHED", 3);
        insertProject(DISMISSED_PROJECT_ID, "dismissed-project", "Dismissed project", "PUBLISHED", 2);
        insertProject(AVAILABLE_PROJECT_ID, "available-project", "Available project", "PUBLISHED", 1);
        insertProject(DRAFT_PROJECT_ID, "draft-project", "Draft project", "DRAFT", 4);
    }

    @Test
    void persistsSavedAndFeedbackStateAndRecordsOnlyChangedActions() {
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);
        repository.setFeedback(PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.INTERESTED);
        repository.setFeedback(PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.INTERESTED);
        repository.setFeedback(PROFILE_ID, DISMISSED_PROJECT_ID, ProjectFeedbackType.DISMISSED);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isEqualTo(1);
        assertThat(repository.findSavedProjects(PROFILE_ID, 0, 20))
                .extracting(project -> project.id())
                .containsExactly(SAVED_PROJECT_ID);
        assertThat(repository.countInterestedProjects(PROFILE_ID)).isEqualTo(1);
        assertThat(repository.findInterestedProjects(PROFILE_ID, 0, 20))
                .extracting(project -> project.id())
                .containsExactly(SAVED_PROJECT_ID);
        assertThat(repository.countDiscoveryFeed(PROFILE_ID)).isEqualTo(1);
        assertThat(repository.findDiscoveryFeed(PROFILE_ID, List.of(), List.of(), 0, null, 20))
                .extracting(item -> item.project().id())
                .containsExactly(AVAILABLE_PROJECT_ID);
        assertThat(interactionSignals()).containsExactly(
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "SAVED", "FEED"),
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "INTERESTED", "FEED"),
                new InteractionSignal(PROFILE_ID, DISMISSED_PROJECT_ID, "DISMISSED", "FEED"));
    }

    @Test
    void readsOnlyOwnFeedbackAndClearsIdempotentlyWithAppendOnlyEvents() {
        assertThat(repository.findFeedback(PROFILE_ID, SAVED_PROJECT_ID)).isNull();
        repository.clearFeedback(PROFILE_ID, SAVED_PROJECT_ID);
        assertThat(eventTypes()).isEmpty();

        repository.setFeedback(PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.INTERESTED);
        repository.setFeedback(OTHER_PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.DISMISSED);
        assertThat(repository.findFeedback(PROFILE_ID, SAVED_PROJECT_ID))
                .isEqualTo(ProjectFeedbackType.INTERESTED);
        assertThat(repository.findFeedback(OTHER_PROFILE_ID, SAVED_PROJECT_ID))
                .isEqualTo(ProjectFeedbackType.DISMISSED);

        repository.clearFeedback(PROFILE_ID, SAVED_PROJECT_ID);
        repository.clearFeedback(PROFILE_ID, SAVED_PROJECT_ID);
        assertThat(repository.findFeedback(PROFILE_ID, SAVED_PROJECT_ID)).isNull();
        assertThat(repository.findFeedback(OTHER_PROFILE_ID, SAVED_PROJECT_ID))
                .isEqualTo(ProjectFeedbackType.DISMISSED);
        assertThat(repository.countInterestedProjects(PROFILE_ID)).isZero();
        assertThat(repository.countDiscoveryFeed(PROFILE_ID)).isEqualTo(3);
        assertThat(jdbcClient.sql("""
                select count(*) from public.project_feedback
                where profile_id = :profileId and project_id = :projectId
                """)
                .param("profileId", PROFILE_ID)
                .param("projectId", SAVED_PROJECT_ID)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(interactionSignals()).containsExactly(
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "INTERESTED", "FEED"),
                new InteractionSignal(OTHER_PROFILE_ID, SAVED_PROJECT_ID, "DISMISSED", "FEED"),
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "FEEDBACK_CLEARED", "FEED"));

        repository.setFeedback(PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.DISMISSED);
        assertThat(repository.findFeedback(PROFILE_ID, SAVED_PROJECT_ID))
                .isEqualTo(ProjectFeedbackType.DISMISSED);
        assertThat(interactionSignals()).hasSize(4);
    }

    @Test
    void rejectsFeedbackOnUnpublishedAndUnknownProjectsWithoutEvents() {
        assertThatThrownBy(() -> repository.setFeedback(PROFILE_ID, DRAFT_PROJECT_ID,
                ProjectFeedbackType.INTERESTED)).isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> repository.clearFeedback(PROFILE_ID, DRAFT_PROJECT_ID))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> repository.findFeedback(PROFILE_ID, DRAFT_PROJECT_ID))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> repository.setFeedback(PROFILE_ID, UNKNOWN_PROJECT_ID,
                ProjectFeedbackType.DISMISSED)).isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> repository.clearFeedback(PROFILE_ID, UNKNOWN_PROJECT_ID))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThat(eventTypes()).isEmpty();
    }

    @Test
    void rollsBackFeedbackWhenClearEventCannotBeRecorded() {
        repository.setFeedback(PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.INTERESTED);
        jdbcClient.sql("""
                alter table public.interaction_events
                add constraint interaction_events_reject_clear check (event_type <> 'FEEDBACK_CLEARED')
                """).update();
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                repository.clearFeedback(PROFILE_ID, SAVED_PROJECT_ID)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findFeedback(PROFILE_ID, SAVED_PROJECT_ID))
                .isEqualTo(ProjectFeedbackType.INTERESTED);
        assertThat(eventTypes()).containsExactly("INTERESTED");
    }

    @Test
    void unsavesIdempotentlyAndRejectsUnpublishedMutationTargets() {
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, false);
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, false);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isZero();
        assertThat(interactionSignals()).containsExactly(
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "SAVED", "FEED"),
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "UNSAVED", "FEED"));
        assertThatThrownBy(() -> repository.setSaved(PROFILE_ID, DRAFT_PROJECT_ID, true))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> repository.setSaved(PROFILE_ID, DRAFT_PROJECT_ID, false))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> repository.setSaved(PROFILE_ID, UNKNOWN_PROJECT_ID, true))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> repository.setSaved(PROFILE_ID, UNKNOWN_PROJECT_ID, false))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThat(interactionSignals()).containsExactly(
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "SAVED", "FEED"),
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "UNSAVED", "FEED"));
    }

    @Test
    void listsOnlyTheProfilesPublishedSavedProjectsBySavedDateAndStableTieBreak() {
        insertSavedProject(PROFILE_ID, SAVED_PROJECT_ID, "2026-10-08T12:00:00Z");
        insertSavedProject(PROFILE_ID, DISMISSED_PROJECT_ID, "2026-10-09T12:00:00Z");
        insertSavedProject(PROFILE_ID, AVAILABLE_PROJECT_ID, "2026-10-09T12:00:00Z");
        insertSavedProject(PROFILE_ID, DRAFT_PROJECT_ID, "2026-10-10T12:00:00Z");
        insertSavedProject(OTHER_PROFILE_ID, SAVED_PROJECT_ID, "2026-10-11T12:00:00Z");

        assertThat(repository.findSavedProjects(PROFILE_ID, 0, 20))
                .extracting(project -> project.id())
                .containsExactly(AVAILABLE_PROJECT_ID, DISMISSED_PROJECT_ID, SAVED_PROJECT_ID);
        assertThat(repository.countSavedProjects(PROFILE_ID)).isEqualTo(3);
        assertThat(repository.findSavedProjects(OTHER_PROFILE_ID, 0, 20))
                .extracting(project -> project.id())
                .containsExactly(SAVED_PROJECT_ID);
        assertThat(repository.countSavedProjects(OTHER_PROFILE_ID)).isEqualTo(1);
    }

    @Test
    void returnsAnEmptySavedCollectionWhenTheProfileHasNoSavedProjects() {
        assertThat(repository.findSavedProjects(PROFILE_ID, 0, 20)).isEmpty();
        assertThat(repository.countSavedProjects(PROFILE_ID)).isZero();
    }

    @Test
    void allowsProfilesToSaveTheSameProjectAndRemoveOnlyTheirOwnRelation() {
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);
        repository.setSaved(OTHER_PROFILE_ID, SAVED_PROJECT_ID, true);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isEqualTo(1);
        assertThat(repository.countSavedProjects(OTHER_PROFILE_ID)).isEqualTo(1);
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, false);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isZero();
        assertThat(repository.countSavedProjects(OTHER_PROFILE_ID)).isEqualTo(1);
        assertThat(interactionSignals()).containsExactly(
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "SAVED", "FEED"),
                new InteractionSignal(OTHER_PROFILE_ID, SAVED_PROJECT_ID, "SAVED", "FEED"),
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "UNSAVED", "FEED"));
    }

    @Test
    void concurrentIdempotentSaveRetriesCreateOneRelationAndOneEvent() throws Exception {
        runConcurrentSavedTransition(true);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isEqualTo(1);
        assertThat(interactionSignals()).containsExactly(
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "SAVED", "FEED"));
    }

    @Test
    void concurrentIdempotentUnsaveRetriesCreateOneUnsaveEvent() throws Exception {
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);

        runConcurrentSavedTransition(false);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isZero();
        assertThat(interactionSignals()).containsExactly(
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "SAVED", "FEED"),
                new InteractionSignal(PROFILE_ID, SAVED_PROJECT_ID, "UNSAVED", "FEED"));
    }

    private void runConcurrentSavedTransition(boolean saved) throws Exception {
        int attempts = 8;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<?>> saves = new ArrayList<>();

        try {
            for (int attempt = 0; attempt < attempts; attempt++) {
                saves.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent saved-project gate timed out.");
                    }
                    transactionTemplate.executeWithoutResult(status ->
                            repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, saved));
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> save : saves) {
                save.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackSavedStateWhenItsInteractionEventCannotBeRecorded() {
        jdbcClient.sql("""
                alter table public.interaction_events
                add constraint interaction_events_reject_saved check (event_type <> 'SAVED')
                """).update();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isZero();
        assertThat(eventTypes()).isEmpty();
    }

    @Test
    void paginatesOnlyPublishedProjectsWithoutRepeatingRowsWhenPreferencesAreEmpty() {
        jdbcClient.sql("""
                update public.projects
                set published_at = timezone('utc', now()) - interval '2 hours'
                where id in (:savedProjectId, :dismissedProjectId)
                """)
                .param("savedProjectId", SAVED_PROJECT_ID)
                .param("dismissedProjectId", DISMISSED_PROJECT_ID)
                .update();
        List<DiscoveryFeedItem> firstPage = repository.findDiscoveryFeed(
                PROFILE_ID, List.of(), List.of(), 0, null, 2);
        DiscoveryFeedItem lastItem = firstPage.get(firstPage.size() - 1);
        DiscoveryFeedCursor cursor = new DiscoveryFeedCursor(
                lastItem.preferenceScore(),
                lastItem.publishedAt(),
                lastItem.project().id(),
                firstPage.size());

        List<DiscoveryFeedItem> secondPage = repository.findDiscoveryFeed(
                PROFILE_ID, List.of(), List.of(), 0, cursor, 2);

        assertThat(firstPage).extracting(item -> item.project().id())
                .containsExactly(AVAILABLE_PROJECT_ID, DISMISSED_PROJECT_ID);
        assertThat(secondPage).extracting(item -> item.project().id())
                .containsExactly(SAVED_PROJECT_ID);
        assertThat(firstPage).extracting(item -> item.project().id())
                .doesNotContain(DRAFT_PROJECT_ID);
    }

    @Test
    void ranksSectorAndStageMatchesBeforeNewerProjectsWithoutAMatch() {
        insertProject(BOTH_MATCH_PROJECT_ID, "both-match", "Both match", "PUBLISHED", 10,
                SECTOR_ID, "IDEA");
        insertProject(STAGE_MATCH_PROJECT_ID, "stage-match", "Stage match", "PUBLISHED", 2,
                OTHER_SECTOR_ID, "IDEA");
        insertProject(NO_MATCH_PROJECT_ID, "no-match", "No match", "PUBLISHED", 0,
                OTHER_SECTOR_ID, "MVP");

        List<DiscoveryFeedItem> feed = repository.findDiscoveryFeed(
                PROFILE_ID,
                List.of("sostenibilidad"),
                List.of("IDEA"),
                0,
                null,
                20);

        assertThat(feed.getFirst().project().id()).isEqualTo(BOTH_MATCH_PROJECT_ID);
        assertThat(feed).extracting(item -> item.project().id())
                .containsSubsequence(STAGE_MATCH_PROJECT_ID, NO_MATCH_PROJECT_ID);
        assertThat(feed.getLast().project().id()).isEqualTo(NO_MATCH_PROJECT_ID);
    }

    private void insertProject(
            UUID id,
            String slug,
            String name,
            String status,
            int publishedOrder) {
        insertProject(id, slug, name, status, publishedOrder, SECTOR_ID, "MVP");
    }

    private void insertProject(
            UUID id,
            String slug,
            String name,
            String status,
            int publishedOrder,
            UUID sectorId,
            String stage) {
        jdbcClient.sql("""
                insert into public.projects (
                  id, slug, name, tagline, sector_id, city, stage, status, published_at
                ) values (
                  :id, :slug, :name, :tagline, :sectorId, 'La Paz', :stage, :status,
                  timezone('utc', now()) - (:publishedOrder * interval '1 hour')
                )
                """)
                .param("id", id)
                .param("slug", slug)
                .param("name", name)
                .param("tagline", name + " tagline")
                .param("sectorId", sectorId)
                .param("stage", stage)
                .param("status", status)
                .param("publishedOrder", publishedOrder)
                .update();
    }

    private void insertSavedProject(UUID profileId, UUID projectId, String savedAt) {
        jdbcClient.sql("""
                insert into public.saved_projects (profile_id, project_id, saved_at)
                values (:profileId, :projectId, cast(:savedAt as timestamptz))
                """)
                .param("profileId", profileId)
                .param("projectId", projectId)
                .param("savedAt", savedAt)
                .update();
    }

    private List<String> eventTypes() {
        return jdbcClient.sql("select event_type from public.interaction_events order by id")
                .query(String.class)
                .list();
    }

    private List<InteractionSignal> interactionSignals() {
        return jdbcClient.sql("""
                select profile_id, project_id, event_type, source
                from public.interaction_events
                order by id
                """)
                .query((resultSet, rowNumber) -> new InteractionSignal(
                        resultSet.getObject("profile_id", UUID.class),
                        resultSet.getObject("project_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getString("source")))
                .list();
    }

    private record InteractionSignal(UUID profileId, UUID projectId, String type, String source) {
    }
}
