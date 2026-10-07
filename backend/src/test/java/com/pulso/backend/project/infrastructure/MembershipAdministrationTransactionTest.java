package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.project.application.AdministerProjectMembership;
import com.pulso.backend.project.domain.MembershipOwnerInvariantException;
import com.pulso.backend.project.domain.MembershipPermissionException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class MembershipAdministrationTransactionTest {

    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OWNER_MEMBERSHIP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_MEMBERSHIP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ADMIN_MEMBERSHIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private AnnotationConfigApplicationContext context;
    private JdbcClient jdbcClient;
    private AdministerProjectMembership service;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("drop table if exists public.project_members, public.projects, public.profiles cascade").update();
        jdbcClient.sql("create table public.projects (id uuid primary key)").update();
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("insert into public.profiles (id) values (:owner), (:member), (:admin)")
                .param("owner", OWNER_ID).param("member", MEMBER_ID).param("admin", ADMIN_ID).update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  profile_id uuid references public.profiles (id),
                  role text not null check (role in ('OWNER', 'ADMIN', 'MEMBER')),
                  membership_status text not null check (membership_status in ('INVITED', 'ACTIVE', 'LEFT', 'REMOVED')),
                  created_by_profile_id uuid not null references public.profiles (id),
                  invitee_email text default 'invitee@example.test',
                  invite_token_digest text default 'fixture-digest-never-log',
                  display_title text default 'Team member',
                  is_featured boolean not null default true,
                  is_public boolean not null default false,
                  joined_at timestamptz default '2020-01-01T00:00:00Z',
                  created_at timestamptz not null default '2020-01-01T00:00:00Z',
                  updated_at timestamptz not null default '2020-01-01T00:00:00Z',
                  unique (project_id, profile_id)
                )
                """).update();
        jdbcClient.sql("insert into public.projects (id) values (:id)").param("id", PROJECT_ID).update();
        jdbcClient.sql("""
                insert into public.project_members
                    (id, project_id, profile_id, role, membership_status, created_by_profile_id)
                values
                    (:ownerMembership, :project, :owner, 'OWNER', 'ACTIVE', :owner),
                    (:memberMembership, :project, :member, 'MEMBER', 'ACTIVE', :owner),
                    (:adminMembership, :project, :admin, 'ADMIN', 'ACTIVE', :owner)
                """)
                .param("ownerMembership", OWNER_MEMBERSHIP_ID).param("memberMembership", MEMBER_MEMBERSHIP_ID)
                .param("project", PROJECT_ID).param("owner", OWNER_ID).param("member", MEMBER_ID)
                .param("adminMembership", ADMIN_MEMBERSHIP_ID).param("admin", ADMIN_ID).update();

        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(JdbcMembershipAdministrationRepository.class,
                () -> new JdbcMembershipAdministrationRepository(jdbcClient));
        context.registerBean(AdministerProjectMembership.class);
        context.registerBean(MembershipAdministrationAuditLogger.class);
        context.refresh();
        service = context.getBean(AdministerProjectMembership.class);
        transactions = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "activate, LEFT, MEMBER, ACTIVE",
            "activate, REMOVED, MEMBER, ACTIVE",
            "changeRole, ACTIVE, ADMIN, ACTIVE",
            "withdraw, ACTIVE, MEMBER, REMOVED",
            "withdraw, INVITED, MEMBER, REMOVED"
    })
    void committedActionsAuditOnlySafeFieldsAndPreserveProtectedData(
            String action, String initialStatus, String nextRole, String nextStatus, CapturedOutput output) {
        jdbcClient.sql("update public.project_members set membership_status = :status where id = :id")
                .param("status", initialStatus).param("id", MEMBER_MEMBERSHIP_ID).update();
        if ("INVITED".equals(initialStatus)) {
            jdbcClient.sql("update public.project_members set profile_id = null, joined_at = null where id = :id")
                    .param("id", MEMBER_MEMBERSHIP_ID).update();
        }
        Map<String, Object> before = membershipRow();

        transactions.executeWithoutResult(status -> {
            service.execute(PROJECT_ID, MEMBER_MEMBERSHIP_ID, OWNER_ID, action,
                    "changeRole".equals(action) ? "ADMIN" : null);
            assertThat(auditEvents(output)).isEmpty();
        });

        Map<String, Object> after = membershipRow();
        assertThat(after.get("role")).isEqualTo(nextRole);
        assertThat(after.get("membership_status")).isEqualTo(nextStatus);
        assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
        assertThat(protectedData(after)).isEqualTo(protectedData(before));
        assertThat(jdbcClient.sql("select count(*) from public.profiles").query(Long.class).single()).isEqualTo(3L);
        assertThat(auditEvents(output)).singleElement().satisfies(event -> {
            assertThat(Instant.parse(event.path("timestamp").stringValue())).isNotNull();
            assertThat(event.path("level").stringValue()).isEqualTo("INFO");
            assertThat(event.path("module").stringValue()).isEqualTo("project");
            assertThat(event.path("userId").stringValue()).isEqualTo(OWNER_ID.toString());
            JsonNode metadata = event.path("metadata");
            assertThat(metadata.size()).isEqualTo(7);
            assertThat(metadata.path("action").stringValue()).isEqualTo(action);
            assertThat(metadata.path("projectId").stringValue()).isEqualTo(PROJECT_ID.toString());
            assertThat(metadata.path("memberId").stringValue()).isEqualTo(MEMBER_MEMBERSHIP_ID.toString());
            assertThat(metadata.path("previousRole").stringValue()).isEqualTo("MEMBER");
            assertThat(metadata.path("nextRole").stringValue()).isEqualTo(nextRole);
            assertThat(metadata.path("previousStatus").stringValue()).isEqualTo(initialStatus);
            assertThat(metadata.path("nextStatus").stringValue()).isEqualTo(nextStatus);
            assertThat(event.toString()).doesNotContain("invitee@example.test", "fixture-digest-never-log",
                    "token", "secret", "email", "profileId", "Team member");
        });
    }

    @Test
    void laterFailureRollsBackTheMembershipAndDoesNotAudit(CapturedOutput output) {
        Map<String, Object> before = membershipRow();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            service.execute(PROJECT_ID, MEMBER_MEMBERSHIP_ID, OWNER_ID, "withdraw", null);
            assertThat(auditEvents(output)).isEmpty();
            throw new IllegalStateException("later operation failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("later operation failed");

        assertThat(membershipRow()).isEqualTo(before);
        assertThat(auditEvents(output)).isEmpty();
    }

    @Test
    void databaseCommitFailureRollsBackTheMembershipAndDoesNotAudit(CapturedOutput output) {
        Map<String, Object> before = membershipRow();
        jdbcClient.sql("""
                create or replace function reject_membership_commit() returns trigger language plpgsql as $$
                begin
                  raise exception 'fixture commit failure';
                end;
                $$
                """).update();
        jdbcClient.sql("""
                create constraint trigger reject_membership_commit
                after update on public.project_members deferrable initially deferred
                for each row execute function reject_membership_commit()
                """).update();

        assertThatThrownBy(() -> service.execute(
                PROJECT_ID, MEMBER_MEMBERSHIP_ID, OWNER_ID, "changeRole", "ADMIN"))
                .isInstanceOf(TransactionException.class);

        assertThat(membershipRow()).isEqualTo(before);
        assertThat(auditEvents(output)).isEmpty();
    }

    @Test
    void permissionRejectionLeavesAllPersistedFieldsUntouchedAndDoesNotAudit(CapturedOutput output) {
        Map<String, Object> before = membershipRow();

        assertThatThrownBy(() -> service.execute(
                PROJECT_ID, OWNER_MEMBERSHIP_ID, MEMBER_ID, "changeRole", "ADMIN"))
                .isInstanceOf(MembershipPermissionException.class);

        assertThat(membershipRow()).isEqualTo(before);
        assertThat(auditEvents(output)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"changeRole, changeRole", "withdraw, withdraw", "changeRole, withdraw", "withdraw, changeRole"})
    void concurrentOwnerReductionsCannotBothCommit(
            String firstAction, String secondAction, CapturedOutput output) throws Exception {
        jdbcClient.sql("update public.project_members set role = 'OWNER' where id = :id")
                .param("id", MEMBER_MEMBERSHIP_ID).update();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                service.execute(PROJECT_ID, OWNER_MEMBERSHIP_ID, ADMIN_ID, firstAction,
                        "changeRole".equals(firstAction) ? "ADMIN" : null);
                firstUpdated.countDown();
                await(commitFirst);
            }));
            assertThat(firstUpdated.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> {
                assertThatThrownBy(() -> service.execute(
                        PROJECT_ID, MEMBER_MEMBERSHIP_ID, ADMIN_ID, secondAction,
                        "changeRole".equals(secondAction) ? "ADMIN" : null))
                        .isInstanceOf(MembershipOwnerInvariantException.class);
            });
            // Observe a real PostgreSQL waiter before allowing the first commit.
            awaitProjectLockWaiter();
            assertThat(auditEvents(output)).isEmpty();
            commitFirst.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            commitFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbcClient.sql("""
                select count(*) from public.project_members
                where project_id = :id and role = 'OWNER' and membership_status = 'ACTIVE'
                """).param("id", PROJECT_ID).query(Long.class).single()).isEqualTo(1L);
        assertThat(context.getBean(JdbcMembershipAdministrationRepository.class)
                .findMember(PROJECT_ID, MEMBER_MEMBERSHIP_ID).orElseThrow().role()).isEqualTo("OWNER");
        assertThat(auditEvents(output)).singleElement().satisfies(event -> {
            assertThat(event.path("userId").stringValue()).isEqualTo(ADMIN_ID.toString());
            assertThat(event.path("metadata").path("action").stringValue()).isEqualTo(firstAction);
            assertThat(event.path("metadata").path("memberId").stringValue()).isEqualTo(OWNER_MEMBERSHIP_ID.toString());
        });
    }

    private void awaitProjectLockWaiter() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            long waiters = jdbcClient.sql("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock'
                      and query like '%from public.projects%' and query like '%for update%'
                    """).query(Long.class).single();
            if (waiters > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Second administration did not wait for the project lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to commit the first administration");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Administration interrupted", exception);
        }
    }

    private Map<String, Object> membershipRow() {
        return jdbcClient.sql("select * from public.project_members where id = :id")
                .param("id", MEMBER_MEMBERSHIP_ID)
                .query((row, rowNumber) -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (int i = 1; i <= row.getMetaData().getColumnCount(); i++) {
                        values.put(row.getMetaData().getColumnName(i), row.getObject(i));
                    }
                    return values;
                }).single();
    }

    private static Map<String, Object> protectedData(Map<String, Object> row) {
        Map<String, Object> values = new LinkedHashMap<>(row);
        values.remove("role");
        values.remove("membership_status");
        values.remove("updated_at");
        return values;
    }

    private static List<JsonNode> auditEvents(CapturedOutput output) {
        List<JsonNode> events = new ArrayList<>();
        for (String line : output.getAll().lines().toList()) {
            int start = line.indexOf("{\"timestamp\"");
            if (start >= 0) {
                JsonNode event = JSON.readTree(line.substring(start));
                if ("administerMembership".equals(event.path("operation").stringValue())) {
                    events.add(event);
                }
            }
        }
        return events;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }
}
