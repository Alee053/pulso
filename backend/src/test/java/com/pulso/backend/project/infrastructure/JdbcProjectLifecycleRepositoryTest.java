package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.project.domain.ProjectLifecycleState;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectLifecycleRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID MISSING_PROJECT_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OWNER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID MEMBER_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID INVITED_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final UUID LEFT_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888");
    private static final UUID REMOVED_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final UUID EXTERNAL_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-01T12:00:00Z");
    private static final Instant ARCHIVED_AT = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-09-19T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcProjectLifecycleRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcProjectLifecycleRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.project_members, public.projects cascade")
                .update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key,
                  status text not null,
                  published_at timestamptz,
                  archived_at timestamptz,
                  reviewed_by_profile_id uuid,
                  review_note text,
                  reviewed_at timestamptz
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  profile_id uuid,
                  role text not null,
                  membership_status text not null
                )
                """).update();
        jdbcClient.sql("""
                insert into public.projects (id, status, published_at, archived_at) values
                  (:projectId, 'DRAFT', null, null),
                  (:otherProjectId, 'DRAFT', null, null)
                """)
                .param("projectId", PROJECT_ID)
                .param("otherProjectId", OTHER_PROJECT_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (id, project_id, profile_id, role, membership_status) values
                  ('10000000-0000-0000-0000-000000000001', :projectId, :ownerId, 'OWNER', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000002', :projectId, :adminId, 'ADMIN', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000003', :projectId, :memberId, 'MEMBER', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000004', :projectId, :invitedId, 'ADMIN', 'INVITED'),
                  ('10000000-0000-0000-0000-000000000005', :projectId, :leftId, 'OWNER', 'LEFT'),
                  ('10000000-0000-0000-0000-000000000006', :projectId, :removedId, 'ADMIN', 'REMOVED')
                """)
                .param("projectId", PROJECT_ID)
                .param("ownerId", OWNER_ID)
                .param("adminId", ADMIN_ID)
                .param("memberId", MEMBER_ID)
                .param("invitedId", INVITED_ID)
                .param("leftId", LEFT_ID)
                .param("removedId", REMOVED_ID)
                .update();
    }

    @Test
    void loadsTheMembershipForTheRequestedProjectAndJwtProfile() {
        var snapshot = repository.findForActor(PROJECT_ID, OWNER_ID).orElseThrow();

        assertThat(snapshot.projectId()).isEqualTo(PROJECT_ID);
        assertThat(snapshot.profileId()).isEqualTo(OWNER_ID);
        assertThat(snapshot.state().status()).isEqualTo("DRAFT");
        assertThat(snapshot.actor().role()).isEqualTo("OWNER");
        assertThat(snapshot.actor().membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void doesNotResolveMembershipFromAnotherProjectOrAnExternalProfile() {
        assertThat(repository.findForActor(OTHER_PROJECT_ID, OWNER_ID)).isEmpty();
        assertThat(repository.findForActor(PROJECT_ID, EXTERNAL_ID)).isEmpty();
    }

    @Test
    void doesNotResolveAMembershipForANonexistentProject() {
        assertThat(repository.findForActor(MISSING_PROJECT_ID, OWNER_ID)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER, ACTIVE, 11111111-1111-1111-1111-111111111111",
            "ADMIN, ACTIVE, 55555555-5555-5555-5555-555555555555"
    })
    void updatesOnlyForAnActiveOwnerOrAdmin(
            String role,
            String membershipStatus,
            UUID profileId) {
        assertThat(role).isIn("OWNER", "ADMIN");
        assertThat(membershipStatus).isEqualTo("ACTIVE");

        int updated = repository.updateLifecycle(
                PROJECT_ID,
                profileId,
                "DRAFT",
                new ProjectLifecycleState("PENDING_REVIEW", null, null));

        assertThat(updated).isEqualTo(1);
        assertThat(statusOf(PROJECT_ID)).isEqualTo("PENDING_REVIEW");
    }

    @ParameterizedTest
    @CsvSource({
            "66666666-6666-6666-6666-666666666666",
            "77777777-7777-7777-7777-777777777777",
            "88888888-8888-8888-8888-888888888888",
            "99999999-9999-9999-9999-999999999999",
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    })
    void refusesUpdatesForMembersWithoutAnActiveAdminRole(UUID profileId) {
        int updated = repository.updateLifecycle(
                PROJECT_ID,
                profileId,
                "DRAFT",
                new ProjectLifecycleState("PENDING_REVIEW", null, null));

        assertThat(updated).isZero();
        assertThat(statusOf(PROJECT_ID)).isEqualTo("DRAFT");
    }

    @Test
    void refusesATeamRepositoryWriteDirectlyToPublished() {
        int updated = repository.updateLifecycle(
                PROJECT_ID,
                OWNER_ID,
                "DRAFT",
                new ProjectLifecycleState(
                        "PUBLISHED",
                        Instant.parse("2026-09-20T12:00:00Z"),
                        null));

        assertThat(updated).isZero();
        assertThat(statusOf(PROJECT_ID)).isEqualTo("DRAFT");
    }

    @Test
    void leavesReviewDataUntouchedDuringTeamLifecycleUpdates() {
        jdbcClient.sql("""
                update public.projects
                set reviewed_by_profile_id = :reviewerId,
                    review_note = 'keep this note',
                    reviewed_at = :reviewedAt
                where id = :projectId
                """)
                .param("reviewerId", REVIEWER_ID)
                .param("reviewedAt", REVIEWED_AT.atOffset(ZoneOffset.UTC))
                .param("projectId", PROJECT_ID)
                .update();

        int updated = repository.updateLifecycle(
                PROJECT_ID,
                OWNER_ID,
                "DRAFT",
                new ProjectLifecycleState("PENDING_REVIEW", null, null));

        assertThat(updated).isEqualTo(1);
        var row = jdbcClient.sql("""
                select status, reviewed_by_profile_id, review_note, reviewed_at
                from public.projects
                where id = :projectId
                """)
                .param("projectId", PROJECT_ID)
                .query((resultSet, rowNumber) -> new Object[] {
                        resultSet.getString("status"),
                        resultSet.getObject("reviewed_by_profile_id", UUID.class),
                        resultSet.getString("review_note"),
                        asInstant(resultSet, "reviewed_at")
                })
                .single();

        assertThat(row[0]).isEqualTo("PENDING_REVIEW");
        assertThat(row[1]).isEqualTo(REVIEWER_ID);
        assertThat(row[2]).isEqualTo("keep this note");
        assertThat(row[3]).isEqualTo(REVIEWED_AT);
    }

    @Test
    void archivesWithoutChangingPublishedAt() {
        jdbcClient.sql("""
                update public.projects
                set status = 'PUBLISHED', published_at = :publishedAt
                where id = :projectId
                """)
                .param("projectId", PROJECT_ID)
                .param("publishedAt", PUBLISHED_AT.atOffset(ZoneOffset.UTC))
                .update();

        int updated = repository.updateLifecycle(
                PROJECT_ID,
                ADMIN_ID,
                "PUBLISHED",
                new ProjectLifecycleState("ARCHIVED", PUBLISHED_AT, ARCHIVED_AT));

        assertThat(updated).isEqualTo(1);
        var row = jdbcClient.sql("""
                select status, published_at, archived_at
                from public.projects
                where id = :projectId
                """)
                .param("projectId", PROJECT_ID)
                .query((resultSet, rowNumber) -> new Object[] {
                        resultSet.getString("status"),
                        asInstant(resultSet, "published_at"),
                        asInstant(resultSet, "archived_at")
                })
                .single();

        assertThat(row[0]).isEqualTo("ARCHIVED");
        assertThat(row[1]).isEqualTo(PUBLISHED_AT);
        assertThat(row[2]).isEqualTo(ARCHIVED_AT);
    }

    private String statusOf(UUID projectId) {
        return jdbcClient.sql("select status from public.projects where id = :projectId")
                .param("projectId", projectId)
                .query(String.class)
                .single();
    }

    private static Instant asInstant(
            ResultSet resultSet,
            String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
