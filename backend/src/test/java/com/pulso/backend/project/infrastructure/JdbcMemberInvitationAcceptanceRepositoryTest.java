package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.application.AcceptMemberInvitation;
import com.pulso.backend.project.application.MemberInvitationNotFoundException;
import com.pulso.backend.project.application.PersistedMemberInvitation;

@Testcontainers(disabledWithoutDocker = true)
class JdbcMemberInvitationAcceptanceRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID CREATOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCEPTING_PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID INVITATION_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final String INVITEE_EMAIL = "ada@example.com";
    private static final String PLAINTEXT_TOKEN = "accept-me";
    private static final String TOKEN_DIGEST =
            "69a24810112e8f3e257dfd85e240bcc8e8a60155787eaa0ec9526f584f0deb7d";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcMemberInvitationRepository repository;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcMemberInvitationRepository(
                jdbcClient,
                new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254));
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        jdbcClient.sql("drop table if exists public.project_members, public.projects, public.profiles cascade")
                .update();
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("create table public.projects (id uuid primary key)").update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  profile_id uuid references public.profiles (id),
                  invitee_email text,
                  invite_token_digest text unique,
                  role text not null,
                  membership_status text not null,
                  created_by_profile_id uuid not null references public.profiles (id),
                  joined_at timestamptz,
                  updated_at timestamptz not null default timezone('utc', now())
                )
                """).update();
        jdbcClient.sql("create unique index project_members_project_profile_key "
                + "on public.project_members (project_id, profile_id)").update();
        jdbcClient.sql("insert into public.profiles (id) values (:creator), (:accepting), (:other)")
                .param("creator", CREATOR_ID)
                .param("accepting", ACCEPTING_PROFILE_ID)
                .param("other", OTHER_PROFILE_ID)
                .update();
        jdbcClient.sql("insert into public.projects (id) values (:projectId)")
                .param("projectId", PROJECT_ID)
                .update();
    }

    @Test
    void acceptsTheInvitationOnTheCanonicalRowAndClearsTheDigest() {
        insertPendingInvitation();

        PersistedMemberInvitation accepted = new AcceptMemberInvitation(repository).execute(
                ACCEPTING_PROFILE_ID,
                " Ada@Example.COM ",
                PLAINTEXT_TOKEN);

        assertThat(accepted.invitation().profileId()).isEqualTo(ACCEPTING_PROFILE_ID);
        assertThat(accepted.invitation().membershipStatus()).isEqualTo("ACTIVE");
        assertThat(accepted.invitation().role()).isEqualTo("MEMBER");
        assertThat(jdbcClient.sql("""
                select count(*)
                from public.project_members
                where id = :invitationId
                  and project_id = :projectId
                  and profile_id = :profileId
                  and role = 'MEMBER'
                  and membership_status = 'ACTIVE'
                  and joined_at is not null
                  and invite_token_digest is null
                """)
                .param("invitationId", INVITATION_ID)
                .param("projectId", PROJECT_ID)
                .param("profileId", ACCEPTING_PROFILE_ID)
                .query(Long.class)
                .single()).isEqualTo(1L);
        assertThat(countMembers()).isEqualTo(1L);
    }

    @Test
    void rejectsAnEmailMismatchWithoutModifyingTheInvitation() {
        insertPendingInvitation();

        assertThatThrownBy(() -> new AcceptMemberInvitation(repository).execute(
                ACCEPTING_PROFILE_ID,
                "other@example.com",
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertPendingInvitation();
    }

    @Test
    void rejectsInvalidTokensWithoutModifyingTheInvitation() {
        insertPendingInvitation();

        assertThatThrownBy(() -> new AcceptMemberInvitation(repository).execute(
                ACCEPTING_PROFILE_ID,
                INVITEE_EMAIL,
                "wrong-token"))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertPendingInvitation();
    }

    @Test
    void rejectsAReplayForTheSameOrAnotherProfileAfterTheFirstAcceptance() {
        insertPendingInvitation();
        AcceptMemberInvitation acceptance = new AcceptMemberInvitation(repository);

        acceptance.execute(ACCEPTING_PROFILE_ID, INVITEE_EMAIL, PLAINTEXT_TOKEN);

        assertThatThrownBy(() -> acceptance.execute(
                ACCEPTING_PROFILE_ID,
                INVITEE_EMAIL,
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);
        assertThatThrownBy(() -> acceptance.execute(
                OTHER_PROFILE_ID,
                INVITEE_EMAIL,
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertThat(countMembers()).isEqualTo(1L);
        assertThat(jdbcClient.sql("select profile_id from public.project_members where id = :id")
                .param("id", INVITATION_ID)
                .query(UUID.class)
                .single()).isEqualTo(ACCEPTING_PROFILE_ID);
    }

    @Test
    void serializesConcurrentAcceptanceSoOnlyOneProfileCanClaimTheRow() throws Exception {
        insertPendingInvitation();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> attemptAcceptance(
                    start,
                    ACCEPTING_PROFILE_ID));
            Future<Boolean> second = executor.submit(() -> attemptAcceptance(
                    start,
                    OTHER_PROFILE_ID));
            start.countDown();

            assertThat(first.get()).isNotEqualTo(second.get());
        } finally {
            executor.shutdownNow();
        }

        assertThat(countMembers()).isEqualTo(1L);
        assertThat(jdbcClient.sql("""
                select count(*)
                from public.project_members
                where id = :id
                  and membership_status = 'ACTIVE'
                  and joined_at is not null
                  and invite_token_digest is null
                """)
                .param("id", INVITATION_ID)
                .query(Long.class)
                .single()).isEqualTo(1L);
    }

    private boolean attemptAcceptance(CountDownLatch start, UUID profileId)
            throws InterruptedException {
        start.await();
        try {
            transactionTemplate.execute(status -> {
                new AcceptMemberInvitation(repository).execute(
                        profileId,
                        INVITEE_EMAIL,
                        PLAINTEXT_TOKEN);
                return null;
            });
            return true;
        } catch (MemberInvitationNotFoundException exception) {
            return false;
        }
    }

    private void insertPendingInvitation() {
        jdbcClient.sql("""
                insert into public.project_members (
                    id,
                    project_id,
                    invitee_email,
                    invite_token_digest,
                    role,
                    membership_status,
                    created_by_profile_id
                ) values (
                    :id,
                    :projectId,
                    :inviteeEmail,
                    :inviteTokenDigest,
                    'MEMBER',
                    'INVITED',
                    :creatorId
                )
                """)
                .param("id", INVITATION_ID)
                .param("projectId", PROJECT_ID)
                .param("inviteeEmail", INVITEE_EMAIL)
                .param("inviteTokenDigest", TOKEN_DIGEST)
                .param("creatorId", CREATOR_ID)
                .update();
    }

    private void assertPendingInvitation() {
        assertThat(jdbcClient.sql("""
                select count(*)
                from public.project_members
                where id = :id
                  and profile_id is null
                  and membership_status = 'INVITED'
                  and role = 'MEMBER'
                  and joined_at is null
                  and invite_token_digest = :digest
                """)
                .param("id", INVITATION_ID)
                .param("digest", TOKEN_DIGEST)
                .query(Long.class)
                .single()).isEqualTo(1L);
    }

    private long countMembers() {
        return jdbcClient.sql("select count(*) from public.project_members")
                .query(Long.class)
                .single();
    }
}
