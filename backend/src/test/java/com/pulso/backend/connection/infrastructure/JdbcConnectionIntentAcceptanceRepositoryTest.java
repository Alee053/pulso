package com.pulso.backend.connection.infrastructure;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.connection.application.AcceptConnectionIntent;
import com.pulso.backend.connection.application.ConnectionIntentConflictException;
import com.pulso.backend.connection.application.ConnectionIntentPermissionException;
import com.pulso.backend.connection.domain.AcceptedConnectionConversation;

@Testcontainers(disabledWithoutDocker = true)
class JdbcConnectionIntentAcceptanceRepositoryTest {

    private static final UUID INTENT_ID = UUID.fromString("27333333-3333-4333-8333-333333333333");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID INITIATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESPONDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private AcceptConnectionIntent acceptance;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        acceptance = new AcceptConnectionIntent(
                new JdbcConnectionIntentAcceptanceRepository(jdbcClient));
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        jdbcClient.sql("""
                drop table if exists public.conversation_participants,
                    public.conversations,
                    public.profile_blocks,
                    public.connection_intents,
                    public.project_members,
                    public.projects,
                    public.profiles cascade
                """).update();
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("create table public.projects (id uuid primary key)").update();
        jdbcClient.sql("""
                create table public.project_members (
                  project_id uuid not null references public.projects (id),
                  profile_id uuid not null references public.profiles (id),
                  role text not null,
                  membership_status text not null,
                  primary key (project_id, profile_id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.connection_intents (
                  id uuid primary key,
                  from_profile_id uuid not null references public.profiles (id),
                  project_id uuid not null references public.projects (id),
                  status text not null,
                  responded_by_profile_id uuid references public.profiles (id),
                  responded_at timestamptz
                )
                """).update();
        jdbcClient.sql("""
                create table public.conversations (
                  id uuid primary key default gen_random_uuid(),
                  conversation_type text not null,
                  project_id uuid not null references public.projects (id),
                  connection_intent_id uuid unique not null references public.connection_intents (id),
                  created_by_profile_id uuid not null references public.profiles (id),
                  status text not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.conversation_participants (
                  conversation_id uuid not null references public.conversations (id),
                  profile_id uuid not null references public.profiles (id),
                  participant_role text not null,
                  primary key (conversation_id, profile_id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.profile_blocks (
                  blocker_profile_id uuid not null references public.profiles (id),
                  blocked_profile_id uuid not null references public.profiles (id),
                  primary key (blocker_profile_id, blocked_profile_id)
                )
                """).update();

        jdbcClient.sql("insert into public.profiles (id) values (:initiator), (:responder), (:other)")
                .param("initiator", INITIATOR_ID)
                .param("responder", RESPONDER_ID)
                .param("other", OTHER_MEMBER_ID)
                .update();
        jdbcClient.sql("insert into public.projects (id) values (:projectId)")
                .param("projectId", PROJECT_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (
                    project_id, profile_id, role, membership_status
                ) values
                    (:projectId, :responderId, 'MEMBER', 'ACTIVE'),
                    (:projectId, :otherMemberId, 'OWNER', 'ACTIVE')
                """)
                .param("projectId", PROJECT_ID)
                .param("responderId", RESPONDER_ID)
                .param("otherMemberId", OTHER_MEMBER_ID)
                .update();
        insertPendingIntent();
    }

    @Test
    void acceptsAndCreatesExactlyOneConversationWithBothParticipants() {
        AcceptedConnectionConversation conversation = accept(RESPONDER_ID);

        assertThat(conversation.connectionIntentId()).isEqualTo(INTENT_ID);
        assertThat(conversation.responderProfileId()).isEqualTo(RESPONDER_ID);
        assertThat(count("conversations")).isEqualTo(1);
        assertThat(count("conversation_participants")).isEqualTo(2);
        assertThat(jdbcClient.sql("""
                select string_agg(participant_role, ',' order by participant_role)
                from public.conversation_participants
                where conversation_id = :conversationId
                """)
                .param("conversationId", conversation.conversationId())
                .query(String.class)
                .single()).isEqualTo("INITIATOR,PROJECT_MEMBER");
        assertThat(jdbcClient.sql("select status from public.connection_intents where id = :id")
                .param("id", INTENT_ID)
                .query(String.class)
                .single()).isEqualTo("ACCEPTED");
    }

    @Test
    void returnsTheExistingConversationForTheSameResponder() {
        AcceptedConnectionConversation first = accept(RESPONDER_ID);

        AcceptedConnectionConversation retry = accept(RESPONDER_ID);

        assertThat(retry.conversationId()).isEqualTo(first.conversationId());
        assertThat(count("conversations")).isEqualTo(1);
        assertThat(count("conversation_participants")).isEqualTo(2);
    }

    @Test
    void rejectsBlocksInEitherDirectionWithoutChangingTheIntent() {
        insertBlock(INITIATOR_ID, RESPONDER_ID);
        assertBlocked(RESPONDER_ID);

        jdbcClient.sql("delete from public.profile_blocks").update();
        insertBlock(RESPONDER_ID, INITIATOR_ID);
        assertBlocked(RESPONDER_ID);
    }

    @Test
    void serializesConcurrentAcceptanceAndCreatesOnlyOneConversation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> attemptAcceptance(start, RESPONDER_ID));
            Future<Boolean> second = executor.submit(() -> attemptAcceptance(start, OTHER_MEMBER_ID));
            start.countDown();

            assertThat(first.get()).isNotEqualTo(second.get());
        } finally {
            executor.shutdownNow();
        }

        assertThat(count("conversations")).isEqualTo(1);
        assertThat(count("conversation_participants")).isEqualTo(2);
    }

    @Test
    void rollsBackTheAcceptanceWhenParticipantCreationFails() {
        jdbcClient.sql("""
                create function reject_project_member_participant()
                returns trigger language plpgsql as $$
                begin
                  if new.participant_role = 'PROJECT_MEMBER' then
                    raise exception 'participant rejected';
                  end if;
                  return new;
                end
                $$
                """).update();
        jdbcClient.sql("""
                create trigger reject_project_member
                before insert on public.conversation_participants
                for each row execute function reject_project_member_participant()
                """).update();

        assertThatThrownBy(() -> accept(RESPONDER_ID))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcClient.sql("select status from public.connection_intents where id = :id")
                .param("id", INTENT_ID)
                .query(String.class)
                .single()).isEqualTo("PENDING");
        assertThat(count("conversations")).isZero();
        assertThat(count("conversation_participants")).isZero();
    }

    private AcceptedConnectionConversation accept(UUID responderId) {
        return transactionTemplate.execute(status -> acceptance.execute(INTENT_ID, responderId));
    }

    private boolean attemptAcceptance(CountDownLatch start, UUID responderId)
            throws InterruptedException {
        start.await();
        try {
            accept(responderId);
            return true;
        } catch (ConnectionIntentConflictException exception) {
            return false;
        }
    }

    private void assertBlocked(UUID responderId) {
        assertThatThrownBy(() -> accept(responderId))
                .isInstanceOf(ConnectionIntentPermissionException.class);
        assertThat(jdbcClient.sql("select status from public.connection_intents where id = :id")
                .param("id", INTENT_ID)
                .query(String.class)
                .single()).isEqualTo("PENDING");
        assertThat(count("conversations")).isZero();
    }

    private void insertPendingIntent() {
        jdbcClient.sql("""
                insert into public.connection_intents (
                    id, from_profile_id, project_id, status
                ) values (
                    :id, :initiatorId, :projectId, 'PENDING'
                )
                """)
                .param("id", INTENT_ID)
                .param("initiatorId", INITIATOR_ID)
                .param("projectId", PROJECT_ID)
                .update();
    }

    private void insertBlock(UUID blockerId, UUID blockedId) {
        jdbcClient.sql("""
                insert into public.profile_blocks (blocker_profile_id, blocked_profile_id)
                values (:blockerId, :blockedId)
                """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .update();
    }

    private long count(String table) {
        return jdbcClient.sql("select count(*) from public." + table)
                .query(Long.class)
                .single();
    }
}
