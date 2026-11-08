package com.pulso.backend.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationReadFilter;
import com.pulso.backend.notification.domain.NotificationType;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
class JdbcNotificationRepositoryTest {

    private static final Path NOTIFICATIONS_MIGRATION = Path.of(
            "..", "database", "supabase", "migrations", "20261108120000_create_notifications.sql");

    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID INTENT_ID = UUID.fromString("29333333-3333-4333-8333-333333333333");
    private static final UUID CONVERSATION_ID = UUID.fromString("29444444-4444-4444-8444-444444444444");
    private static final UUID SENDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MEMBER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LEFT_MEMBER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID BLOCKED_BY_SENDER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID BLOCKING_SENDER_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private DataSource dataSource;
    private JdbcClient jdbcClient;
    private TransactionTemplate transactionTemplate;
    private JdbcNotificationRepository repository;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        repository = new JdbcNotificationRepository(jdbcClient, JsonMapper.builder().build());

        jdbcClient.sql("""
                drop table if exists public.notifications,
                    public.profile_blocks,
                    public.conversation_participants,
                    public.conversations,
                    public.connection_intents,
                    public.project_members,
                    public.projects,
                    public.profiles cascade
                """).update();
        jdbcClient.sql("drop function if exists public.protect_notification_identity() cascade").update();
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("create table public.projects (id uuid primary key)").update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key default gen_random_uuid(),
                  project_id uuid not null references public.projects (id),
                  profile_id uuid references public.profiles (id),
                  membership_status text not null
                )
                """).update();
        jdbcClient.sql("create table public.connection_intents (id uuid primary key)").update();
        jdbcClient.sql("""
                create table public.conversations (
                  id uuid primary key,
                  project_id uuid references public.projects (id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.conversation_participants (
                  conversation_id uuid not null references public.conversations (id),
                  profile_id uuid not null references public.profiles (id),
                  left_at timestamptz,
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
        applyNotificationsMigration();

        for (UUID profile : List.of(
                SENDER_ID, OWNER_ID, MEMBER_ID, LEFT_MEMBER_ID, BLOCKED_BY_SENDER_ID, BLOCKING_SENDER_ID)) {
            jdbcClient.sql("insert into public.profiles (id) values (:id)").param("id", profile).update();
        }
        jdbcClient.sql("insert into public.projects (id) values (:id)").param("id", PROJECT_ID).update();
        insertMember(OWNER_ID, "ACTIVE");
        insertMember(MEMBER_ID, "ACTIVE");
        insertMember(SENDER_ID, "ACTIVE");
        insertMember(LEFT_MEMBER_ID, "LEFT");
        insertMember(BLOCKED_BY_SENDER_ID, "ACTIVE");
        insertMember(BLOCKING_SENDER_ID, "ACTIVE");
        insertMember(null, "INVITED");
        jdbcClient.sql("insert into public.profile_blocks values (:blocker, :blocked)")
                .param("blocker", SENDER_ID)
                .param("blocked", BLOCKED_BY_SENDER_ID)
                .update();
        jdbcClient.sql("insert into public.profile_blocks values (:blocker, :blocked)")
                .param("blocker", BLOCKING_SENDER_ID)
                .param("blocked", SENDER_ID)
                .update();
        jdbcClient.sql("insert into public.connection_intents (id) values (:id)")
                .param("id", INTENT_ID)
                .update();
        jdbcClient.sql("insert into public.conversations (id, project_id) values (:id, :projectId)")
                .param("id", CONVERSATION_ID)
                .param("projectId", PROJECT_ID)
                .update();
    }

    @Test
    void aConnectionRequestNotifiesActiveMembersExceptTheSenderAndBlockedProfiles() {
        transactionTemplate.executeWithoutResult(status ->
                repository.connectionRequested(INTENT_ID, PROJECT_ID, SENDER_ID));

        assertThat(recipients()).containsExactlyInAnyOrder(OWNER_ID, MEMBER_ID);
        Notification notification = repository.findPage(OWNER_ID, NotificationReadFilter.ALL, 0, 20).getFirst();
        assertThat(notification.type()).isEqualTo(NotificationType.CONNECTION_REQUEST);
        assertThat(notification.title()).isEqualTo("New connection request");
        assertThat(notification.actorProfileId()).isEqualTo(SENDER_ID);
        assertThat(notification.projectId()).isEqualTo(PROJECT_ID);
        assertThat(notification.connectionIntentId()).isEqualTo(INTENT_ID);
        assertThat(notification.conversationId()).isNull();
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void notifyingTheSameConnectionRequestTwiceDoesNotDuplicateNotices() {
        transactionTemplate.executeWithoutResult(status ->
                repository.connectionRequested(INTENT_ID, PROJECT_ID, SENDER_ID));
        transactionTemplate.executeWithoutResult(status ->
                repository.connectionRequested(INTENT_ID, PROJECT_ID, SENDER_ID));

        assertThat(count()).isEqualTo(2);
    }

    @Test
    void anAcceptedConnectionNotifiesOnlyTheInitiatorWithTheConversation() {
        transactionTemplate.executeWithoutResult(status ->
                repository.connectionAccepted(INTENT_ID, PROJECT_ID, CONVERSATION_ID, SENDER_ID, OWNER_ID));
        transactionTemplate.executeWithoutResult(status ->
                repository.connectionAccepted(INTENT_ID, PROJECT_ID, CONVERSATION_ID, SENDER_ID, OWNER_ID));

        assertThat(recipients()).containsExactly(SENDER_ID);
        Notification notification = repository.findPage(SENDER_ID, NotificationReadFilter.ALL, 0, 20).getFirst();
        assertThat(notification.type()).isEqualTo(NotificationType.CONNECTION_RESPONSE);
        assertThat(notification.actorProfileId()).isEqualTo(OWNER_ID);
        assertThat(notification.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(notification.connectionIntentId()).isEqualTo(INTENT_ID);
        assertThat(notification.payload()).containsEntry("response", "ACCEPTED");
    }

    @Test
    void aNewMessageNotifiesTheOtherActiveParticipantsWithoutCopyingTheText() {
        for (UUID participant : List.of(SENDER_ID, OWNER_ID, MEMBER_ID, LEFT_MEMBER_ID)) {
            jdbcClient.sql("""
                    insert into public.conversation_participants (conversation_id, profile_id, left_at)
                    values (:conversationId, :profileId, case when :profileId = :leftId then now() end)
                    """)
                    .param("conversationId", CONVERSATION_ID)
                    .param("profileId", participant)
                    .param("leftId", LEFT_MEMBER_ID)
                    .update();
        }
        UUID messageId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                repository.messageSent(CONVERSATION_ID, messageId, SENDER_ID));

        assertThat(recipients()).containsExactlyInAnyOrder(OWNER_ID, MEMBER_ID);
        Notification notification = repository.findPage(OWNER_ID, NotificationReadFilter.ALL, 0, 20).getFirst();
        assertThat(notification.type()).isEqualTo(NotificationType.NEW_MESSAGE);
        assertThat(notification.actorProfileId()).isEqualTo(SENDER_ID);
        assertThat(notification.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(notification.projectId()).isEqualTo(PROJECT_ID);
        assertThat(notification.body()).isNull();
        assertThat(notification.payload()).containsOnly(java.util.Map.entry("messageId", messageId.toString()));
    }

    @Test
    void publishingJoinsTheCallersTransaction() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            repository.connectionRequested(INTENT_ID, PROJECT_ID, SENDER_ID);
            throw new IllegalStateException("the event failed after publishing");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count()).isZero();
    }

    @Test
    void listsMostRecentFirstSeparatesReadFromUnreadAndPaginates() {
        insertNotification("00000000-0000-4000-8000-000000000001", OWNER_ID, "2026-11-08T10:00:00Z", null);
        insertNotification("00000000-0000-4000-8000-000000000002", OWNER_ID, "2026-11-08T12:00:00Z", "2026-11-08T12:30:00Z");
        insertNotification("00000000-0000-4000-8000-000000000003", OWNER_ID, "2026-11-08T11:00:00Z", null);
        insertNotification("00000000-0000-4000-8000-000000000004", OWNER_ID, "2026-11-08T12:00:00Z", null);
        insertNotification("00000000-0000-4000-8000-000000000005", MEMBER_ID, "2026-11-08T13:00:00Z", null);

        assertThat(ids(repository.findPage(OWNER_ID, NotificationReadFilter.ALL, 0, 20)))
                .containsExactly("04", "02", "03", "01");
        assertThat(ids(repository.findPage(OWNER_ID, NotificationReadFilter.UNREAD, 0, 20)))
                .containsExactly("04", "03", "01");
        assertThat(ids(repository.findPage(OWNER_ID, NotificationReadFilter.READ, 0, 20)))
                .containsExactly("02");
        assertThat(ids(repository.findPage(OWNER_ID, NotificationReadFilter.ALL, 1, 2)))
                .containsExactly("02", "03");
        assertThat(repository.countUnread(OWNER_ID)).isEqualTo(3);
        assertThat(repository.countUnread(MEMBER_ID)).isEqualTo(1);
        assertThat(repository.findPage(LEFT_MEMBER_ID, NotificationReadFilter.ALL, 0, 20)).isEmpty();
    }

    @Test
    void expiredNotificationsAreHiddenFromTheListAndTheCounter() {
        insertNotification("00000000-0000-4000-8000-000000000001", OWNER_ID, "2026-11-08T10:00:00Z", null);
        jdbcClient.sql("""
                update public.notifications set expires_at = now() - interval '1 day'
                where id = '00000000-0000-4000-8000-000000000001'
                """).update();
        insertNotification("00000000-0000-4000-8000-000000000002", OWNER_ID, "2026-11-08T11:00:00Z", null);

        assertThat(ids(repository.findPage(OWNER_ID, NotificationReadFilter.ALL, 0, 20))).containsExactly("02");
        assertThat(repository.countUnread(OWNER_ID)).isEqualTo(1);
    }

    @Test
    void onlyTheRecipientCanMarkAsReadAndTheFirstReadTimeIsKept() {
        insertNotification("00000000-0000-4000-8000-000000000001", OWNER_ID, "2026-11-08T10:00:00Z", null);
        UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");

        Optional<Notification> stranger = repository.markRead(id, MEMBER_ID);
        Notification first = repository.markRead(id, OWNER_ID).orElseThrow();
        Notification second = repository.markRead(id, OWNER_ID).orElseThrow();

        assertThat(stranger).isEmpty();
        assertThat(first.isRead()).isTrue();
        assertThat(second.readAt()).isEqualTo(first.readAt());
        assertThat(repository.markRead(UUID.randomUUID(), OWNER_ID)).isEmpty();
        assertThat(repository.countUnread(OWNER_ID)).isZero();
    }

    @Test
    void aStrangerAttemptLeavesTheNotificationUnread() {
        insertNotification("00000000-0000-4000-8000-000000000001", OWNER_ID, "2026-11-08T10:00:00Z", null);

        repository.markRead(UUID.fromString("00000000-0000-4000-8000-000000000001"), MEMBER_ID);

        assertThat(repository.countUnread(OWNER_ID)).isEqualTo(1);
    }

    private void insertMember(UUID profileId, String membershipStatus) {
        jdbcClient.sql("""
                insert into public.project_members (project_id, profile_id, membership_status)
                values (:projectId, :profileId, :status)
                """)
                .param("projectId", PROJECT_ID)
                .param("profileId", profileId)
                .param("status", membershipStatus)
                .update();
    }

    private void insertNotification(String id, UUID recipient, String createdAt, String readAt) {
        jdbcClient.sql("""
                insert into public.notifications (
                    id, recipient_profile_id, notification_type, title, conversation_id,
                    created_at, read_at
                ) values (
                    cast(:id as uuid), :recipient, 'NEW_MESSAGE', 'New message', :conversationId,
                    cast(:createdAt as timestamptz), cast(:readAt as timestamptz)
                )
                """)
                .param("id", id)
                .param("recipient", recipient)
                .param("conversationId", CONVERSATION_ID)
                .param("createdAt", createdAt)
                .param("readAt", readAt)
                .update();
    }

    private Set<UUID> recipients() {
        return Set.copyOf(jdbcClient.sql("select recipient_profile_id from public.notifications")
                .query(UUID.class)
                .list());
    }

    private long count() {
        return jdbcClient.sql("select count(*) from public.notifications").query(Long.class).single();
    }

    private static List<String> ids(List<Notification> notifications) {
        return notifications.stream()
                .map(notification -> notification.id().toString().substring(34))
                .toList();
    }

    private void applyNotificationsMigration() throws SQLException, IOException {
        String script = Files.readString(NOTIFICATIONS_MIGRATION, StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            // Plain PostgreSQL has no Supabase API roles; the migration revokes their grants.
            statement.execute("""
                    do $$
                    begin
                      if not exists (select 1 from pg_roles where rolname = 'anon') then
                        create role anon nologin;
                      end if;
                      if not exists (select 1 from pg_roles where rolname = 'authenticated') then
                        create role authenticated nologin;
                      end if;
                    end
                    $$
                    """);
            statement.execute(script);
        }
    }
}
