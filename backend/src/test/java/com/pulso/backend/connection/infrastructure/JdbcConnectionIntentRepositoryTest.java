package com.pulso.backend.connection.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.connection.application.ConnectionIntentRejectedException;
import com.pulso.backend.connection.application.ConnectionIntentRepository;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;

@Testcontainers(disabledWithoutDocker = true)
class JdbcConnectionIntentRepositoryTest {

    private static final UUID PROFILE_ID = id("11111111-1111-1111-1111-111111111111");
    private static final UUID SUSPENDED_PROFILE_ID = id("11111111-1111-1111-1111-111111111112");
    private static final UUID DELETED_PROFILE_ID = id("11111111-1111-1111-1111-111111111113");
    private static final UUID ACTIVE_SECTOR_ID = id("22222222-2222-2222-2222-222222222221");
    private static final UUID INACTIVE_SECTOR_ID = id("22222222-2222-2222-2222-222222222222");
    private static final UUID PROJECT_ID = id("33333333-3333-3333-3333-333333333331");
    private static final UUID DRAFT_PROJECT_ID = id("33333333-3333-3333-3333-333333333332");
    private static final UUID INACTIVE_SECTOR_PROJECT_ID = id("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = id("33333333-3333-3333-3333-333333333334");
    private static final UUID OPPORTUNITY_ID = id("44444444-4444-4444-4444-444444444441");
    private static final UUID SECOND_OPPORTUNITY_ID = id("44444444-4444-4444-4444-444444444442");
    private static final UUID CLOSED_OPPORTUNITY_ID = id("44444444-4444-4444-4444-444444444443");
    private static final UUID OTHER_PROJECT_OPPORTUNITY_ID = id("44444444-4444-4444-4444-444444444444");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbc;
    private ConnectionIntentRepository repository;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.sql("drop table if exists public.connection_intents, public.project_opportunities, public.projects, public.sectors, public.profiles cascade")
                .update();
        createBaseSchema();
        createRoleIfMissing("anon");
        createRoleIfMissing("authenticated");
        new ResourceDatabasePopulator(new FileSystemResource(migrationPath().toFile())).execute(dataSource);
        insertFixtures();
        repository = new JdbcConnectionIntentRepository(jdbc);
    }

    @Test
    void persistsProjectOnlyIntentWithDatabaseOwnedPendingStatus() {
        ConnectionIntent created = create(
                PROFILE_ID, PROJECT_ID, null, "COLLABORATE", "  looking to collaborate  ");

        assertThat(created.id()).isNotNull();
        assertThat(created.fromProfileId()).isEqualTo(PROFILE_ID);
        assertThat(created.projectId()).isEqualTo(PROJECT_ID);
        assertThat(created.opportunityId()).isNull();
        assertThat(created.reason()).isEqualTo(ConnectionIntentReason.COLLABORATE);
        assertThat(created.message()).isEqualTo("looking to collaborate");
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.createdAt()).isNotNull();
        assertThat(persistedRows()).hasSize(1);
        assertThat(persistedRows().getFirst()).containsEntry("status", "PENDING");
    }

    @Test
    void persistsAnOpportunitySpecificIntentAndAnAbsentOptionalMessage() {
        ConnectionIntent created = create(PROFILE_ID, PROJECT_ID, OPPORTUNITY_ID, "OFFER_SERVICE", "   ");

        assertThat(created.projectId()).isEqualTo(PROJECT_ID);
        assertThat(created.opportunityId()).isEqualTo(OPPORTUNITY_ID);
        assertThat(created.message()).isNull();
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(persistedRows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("opportunity_id", OPPORTUNITY_ID.toString());
            assertThat(row).containsEntry("message", "");
        });
    }

    @Test
    void rejectsMissingUnpublishedOrInactiveSectorProjectsWithoutWriting() {
        assertRejected(ConnectionIntentRejectedException.Reason.CONTEXT_UNAVAILABLE,
                () -> create(PROFILE_ID, id("33333333-3333-3333-3333-333333333339"), null, "OTHER"));
        assertRejected(ConnectionIntentRejectedException.Reason.CONTEXT_UNAVAILABLE,
                () -> create(PROFILE_ID, DRAFT_PROJECT_ID, null, "OTHER"));
        assertRejected(ConnectionIntentRejectedException.Reason.CONTEXT_UNAVAILABLE,
                () -> create(PROFILE_ID, INACTIVE_SECTOR_PROJECT_ID, null, "OTHER"));
        assertNoRows();
    }

    @Test
    void rejectsInactiveAndDeletedSendersWithoutWriting() {
        assertRejected(ConnectionIntentRejectedException.Reason.PROFILE_INACTIVE,
                () -> create(SUSPENDED_PROFILE_ID, PROJECT_ID, null, "OTHER"));
        assertRejected(ConnectionIntentRejectedException.Reason.PROFILE_INACTIVE,
                () -> create(DELETED_PROFILE_ID, PROJECT_ID, null, "OTHER"));
        assertNoRows();
    }

    @Test
    void rejectsMissingClosedAndCrossProjectOpportunitiesWithoutWriting() {
        assertRejected(ConnectionIntentRejectedException.Reason.CONTEXT_UNAVAILABLE,
                () -> create(PROFILE_ID, PROJECT_ID, id("44444444-4444-4444-4444-444444444449"), "OTHER"));
        assertRejected(ConnectionIntentRejectedException.Reason.CONTEXT_UNAVAILABLE,
                () -> create(PROFILE_ID, PROJECT_ID, CLOSED_OPPORTUNITY_ID, "OTHER"));
        assertRejected(ConnectionIntentRejectedException.Reason.CONTEXT_UNAVAILABLE,
                () -> create(PROFILE_ID, PROJECT_ID, OTHER_PROJECT_OPPORTUNITY_ID, "OTHER"));
        assertNoRows();
    }

    @Test
    void treatsProjectAndOpportunityContextsSeparatelyAndRejectsPendingDuplicates() {
        ConnectionIntent projectIntent = create(PROFILE_ID, PROJECT_ID, null, "OTHER");
        ConnectionIntent opportunityIntent = create(PROFILE_ID, PROJECT_ID, OPPORTUNITY_ID, "WORK");

        assertThat(projectIntent.opportunityId()).isNull();
        assertThat(opportunityIntent.opportunityId()).isEqualTo(OPPORTUNITY_ID);
        assertRejected(ConnectionIntentRejectedException.Reason.DUPLICATE_PENDING,
                () -> create(PROFILE_ID, PROJECT_ID, null, "BUY"));
        assertRejected(ConnectionIntentRejectedException.Reason.DUPLICATE_PENDING,
                () -> create(PROFILE_ID, PROJECT_ID, OPPORTUNITY_ID, "BUY"));
        assertThat(persistedRows()).hasSize(2);
    }

    @Test
    void keepsDifferentOpportunityContextsDistinctForTheSameSenderAndProject() {
        ConnectionIntent first = create(PROFILE_ID, PROJECT_ID, OPPORTUNITY_ID, "WORK");
        ConnectionIntent second = create(PROFILE_ID, PROJECT_ID, SECOND_OPPORTUNITY_ID, "WORK");

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(persistedRows()).hasSize(2);
    }

    @Test
    void concurrentProjectOnlyCreatesProduceExactlyOnePendingIntent() throws Exception {
        int workers = 2;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicateRejections = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(workers);
        try {
            var futures = java.util.stream.IntStream.range(0, workers)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to start concurrent creates.");
                        }
                        try {
                            transactions.execute(status -> repository.createPending(
                                    PROFILE_ID,
                                    PROJECT_ID,
                                    null,
                                    ConnectionIntentReason.COLLABORATE,
                                    "Concurrent attempt"));
                            successes.incrementAndGet();
                        } catch (ConnectionIntentRejectedException exception) {
                            if (exception.reason() != ConnectionIntentRejectedException.Reason.DUPLICATE_PENDING) {
                                throw exception;
                            }
                            duplicateRejections.incrementAndGet();
                        }
                        return null;
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicateRejections.get()).isEqualTo(1);
        assertThat(persistedRows()).hasSize(1);
    }

    private ConnectionIntent create(UUID sender, UUID project, UUID opportunity, String reason) {
        return create(sender, project, opportunity, reason, null);
    }

    private ConnectionIntent create(UUID sender, UUID project, UUID opportunity, String reason, String message) {
        return transactions.execute(status -> repository.createPending(
                sender,
                project,
                opportunity,
                ConnectionIntentReason.parse(reason),
                message));
    }

    private void assertRejected(ConnectionIntentRejectedException.Reason reason, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ConnectionIntentRejectedException.class)
                .extracting(exception -> ((ConnectionIntentRejectedException) exception).reason())
                .isEqualTo(reason);
    }

    private void assertNoRows() {
        assertThat(countIntents()).isZero();
    }

    private int countIntents() {
        return jdbc.sql("select count(*) from public.connection_intents")
                .query(Integer.class)
                .single();
    }

    private List<Map<String, String>> persistedRows() {
        return jdbc.sql("select id::text, from_profile_id::text, project_id::text, opportunity_id::text, reason, message, status from public.connection_intents")
                .query((row, rowNumber) -> Map.of(
                        "id", row.getString("id"),
                        "from_profile_id", row.getString("from_profile_id"),
                        "project_id", row.getString("project_id"),
                        "opportunity_id", Objects.toString(row.getString("opportunity_id"), ""),
                        "reason", row.getString("reason"),
                        "message", Objects.toString(row.getString("message"), ""),
                        "status", row.getString("status")))
                .list();
    }

    private void createBaseSchema() {
        jdbc.sql("create table public.profiles (id uuid primary key, account_status text not null default 'ACTIVE', deleted_at timestamptz)").update();
        jdbc.sql("create table public.sectors (id uuid primary key, is_active boolean not null)").update();
        jdbc.sql("create table public.projects (id uuid primary key, status text not null, sector_id uuid not null references public.sectors (id))").update();
        jdbc.sql("create table public.project_opportunities (id uuid primary key, project_id uuid not null references public.projects (id), status text not null)").update();
    }

    private void createRoleIfMissing(String role) {
        boolean exists = jdbc.sql("select exists (select 1 from pg_roles where rolname = :role)")
                .param("role", role)
                .query(Boolean.class)
                .single();
        if (!exists) {
            jdbc.sql("create role " + role).update();
        }
    }

    private void insertFixtures() {
        jdbc.sql("insert into public.profiles (id, account_status) values (:active, 'ACTIVE'), (:suspended, 'SUSPENDED'), (:deleted, 'DELETED')")
                .param("active", PROFILE_ID)
                .param("suspended", SUSPENDED_PROFILE_ID)
                .param("deleted", DELETED_PROFILE_ID)
                .update();
        jdbc.sql("update public.profiles set deleted_at = now() where id = :id")
                .param("id", DELETED_PROFILE_ID)
                .update();
        jdbc.sql("insert into public.sectors (id, is_active) values (:active, true), (:inactive, false)")
                .param("active", ACTIVE_SECTOR_ID)
                .param("inactive", INACTIVE_SECTOR_ID)
                .update();
        jdbc.sql("insert into public.projects (id, status, sector_id) values (:published, 'PUBLISHED', :active), (:draft, 'DRAFT', :active), (:inactiveSector, 'PUBLISHED', :inactive), (:other, 'PUBLISHED', :active)")
                .param("published", PROJECT_ID)
                .param("draft", DRAFT_PROJECT_ID)
                .param("inactiveSector", INACTIVE_SECTOR_PROJECT_ID)
                .param("other", OTHER_PROJECT_ID)
                .param("active", ACTIVE_SECTOR_ID)
                .param("inactive", INACTIVE_SECTOR_ID)
                .update();
        jdbc.sql("insert into public.project_opportunities (id, project_id, status) values (:open, :project, 'OPEN'), (:second, :project, 'OPEN'), (:closed, :project, 'CLOSED'), (:other, :otherProject, 'OPEN')")
                .param("open", OPPORTUNITY_ID)
                .param("second", SECOND_OPPORTUNITY_ID)
                .param("closed", CLOSED_OPPORTUNITY_ID)
                .param("other", OTHER_PROJECT_OPPORTUNITY_ID)
                .param("project", PROJECT_ID)
                .param("otherProject", OTHER_PROJECT_ID)
                .update();
    }

    private static Path migrationPath() {
        String migration = "20261103133534_create_connection_intents.sql";
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("database/supabase/migrations").resolve(migration);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate the connection-intents migration.");
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }
}
