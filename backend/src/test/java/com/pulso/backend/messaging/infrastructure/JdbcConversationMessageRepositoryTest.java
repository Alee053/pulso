package com.pulso.backend.messaging.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.pulso.backend.config.MessageProperties;
import com.pulso.backend.messaging.application.ConversationNotActiveException;
import com.pulso.backend.messaging.application.ConversationNotFoundException;
import com.pulso.backend.messaging.application.ListConversationMessages;
import com.pulso.backend.messaging.application.MessagePage;
import com.pulso.backend.messaging.application.MessagingBlockedException;
import com.pulso.backend.messaging.application.SendTextMessage;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageStatus;
import com.pulso.backend.messaging.domain.MessageType;

@Testcontainers(disabledWithoutDocker = true)
class JdbcConversationMessageRepositoryTest {

    private static final Path MESSAGES_MIGRATION = Path.of(
            "..", "database", "supabase", "migrations", "20261107120000_create_conversation_messages.sql");

    private static final UUID CONVERSATION_ID = UUID.fromString("28444444-4444-4444-8444-444444444444");
    private static final UUID FIRST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OUTSIDER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private DataSource dataSource;
    private JdbcClient jdbcClient;
    private TransactionTemplate transactionTemplate;
    private SendTextMessage sendTextMessage;
    private ListConversationMessages listMessages;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        JdbcConversationMessageRepository repository = new JdbcConversationMessageRepository(jdbcClient);
        sendTextMessage = new SendTextMessage(repository, new MessageProperties(0, 20, 50, 100000, 100));
        listMessages = new ListConversationMessages(repository);

        jdbcClient.sql("""
                drop table if exists public.messages,
                    public.profile_blocks,
                    public.conversation_participants,
                    public.conversations,
                    public.profiles cascade
                """).update();
        jdbcClient.sql("drop function if exists public.validate_message_sender() cascade").update();
        jdbcClient.sql("drop function if exists public.protect_message_identity() cascade").update();
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("""
                create table public.conversations (
                  id uuid primary key,
                  status text not null,
                  last_message_at timestamptz
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
        applyMessagesMigration();

        jdbcClient.sql("insert into public.profiles (id) values (:first), (:second), (:outsider)")
                .param("first", FIRST_ID)
                .param("second", SECOND_ID)
                .param("outsider", OUTSIDER_ID)
                .update();
        jdbcClient.sql("insert into public.conversations (id, status) values (:id, 'ACTIVE')")
                .param("id", CONVERSATION_ID)
                .update();
        jdbcClient.sql("""
                insert into public.conversation_participants (conversation_id, profile_id)
                values (:conversationId, :first), (:conversationId, :second)
                """)
                .param("conversationId", CONVERSATION_ID)
                .param("first", FIRST_ID)
                .param("second", SECOND_ID)
                .update();
    }

    @Test
    void anActiveParticipantPersistsATextMessageAndAdvancesTheConversation() {
        Message message = send(FIRST_ID, "Hola, ¿conversamos?");

        assertThat(message.type()).isEqualTo(MessageType.TEXT);
        assertThat(message.status()).isEqualTo(MessageStatus.SENT);
        assertThat(message.senderProfileId()).isEqualTo(FIRST_ID);
        assertThat(message.body()).isEqualTo("Hola, ¿conversamos?");
        assertThat(message.editedAt()).isNull();
        assertThat(message.deletedAt()).isNull();
        OffsetDateTime lastMessageAt = jdbcClient
                .sql("select last_message_at from public.conversations where id = :id")
                .param("id", CONVERSATION_ID)
                .query(OffsetDateTime.class)
                .single();
        assertThat(lastMessageAt).isEqualTo(message.createdAt());
    }

    @Test
    void anExternalProfileCannotSendOrRead() {
        assertThatThrownBy(() -> send(OUTSIDER_ID, "Hola"))
                .isInstanceOf(ConversationNotFoundException.class);
        send(FIRST_ID, "Mensaje privado");

        assertThatThrownBy(() -> listMessages.execute(CONVERSATION_ID, OUTSIDER_ID, 0, 20))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void aParticipantWhoLeftCannotSendOrRead() {
        send(FIRST_ID, "Antes de salir");
        markAsLeft(SECOND_ID);

        assertThatThrownBy(() -> send(SECOND_ID, "Ya no estoy"))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> listMessages.execute(CONVERSATION_ID, SECOND_ID, 0, 20))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void anUnknownConversationLooksLikeAnInaccessibleOne() {
        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                sendTextMessage.execute(UUID.randomUUID(), FIRST_ID, "Hola")))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> listMessages.execute(UUID.randomUUID(), FIRST_ID, 0, 20))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void conversationsThatAreNotActiveRejectNewMessagesButStayReadable() {
        send(FIRST_ID, "Antes de cerrar");
        for (String status : new String[] {"ARCHIVED", "BLOCKED", "CLOSED"}) {
            jdbcClient.sql("update public.conversations set status = :status where id = :id")
                    .param("status", status)
                    .param("id", CONVERSATION_ID)
                    .update();

            assertThatThrownBy(() -> send(FIRST_ID, "Después de cerrar"))
                    .isInstanceOf(ConversationNotActiveException.class);
            assertThat(listMessages.execute(CONVERSATION_ID, FIRST_ID, 0, 20).messages()).hasSize(1);
        }
    }

    @Test
    void aBlockInEitherDirectionPreventsSendingUnlessTheBlockedProfileLeft() {
        insertBlock(FIRST_ID, SECOND_ID);

        assertThatThrownBy(() -> send(FIRST_ID, "Yo bloqueé"))
                .isInstanceOf(MessagingBlockedException.class);
        assertThatThrownBy(() -> send(SECOND_ID, "Me bloquearon"))
                .isInstanceOf(MessagingBlockedException.class);
        assertThat(messageCount()).isZero();

        markAsLeft(SECOND_ID);
        assertThat(send(FIRST_ID, "Nadie más participa").status()).isEqualTo(MessageStatus.SENT);
    }

    @Test
    void pagesAreOrderedByCreationTimeWithAStableTieBreaker() {
        insertMessage("00000000-0000-4000-8000-000000000003", FIRST_ID, "tercero", "SENT", "2026-11-07T15:03:00Z");
        insertMessage("00000000-0000-4000-8000-000000000001", FIRST_ID, "primero", "SENT", "2026-11-07T15:01:00Z");
        insertMessage("00000000-0000-4000-8000-000000000002", SECOND_ID, "segundo", "SENT", "2026-11-07T15:02:00Z");
        insertMessage("00000000-0000-4000-8000-000000000005", SECOND_ID, "quinto b", "SENT", "2026-11-07T15:05:00Z");
        insertMessage("00000000-0000-4000-8000-000000000004", FIRST_ID, "quinto a", "SENT", "2026-11-07T15:05:00Z");

        MessagePage firstPage = listMessages.execute(CONVERSATION_ID, SECOND_ID, 0, 2);
        MessagePage secondPage = listMessages.execute(CONVERSATION_ID, SECOND_ID, 2, 2);
        MessagePage lastPage = listMessages.execute(CONVERSATION_ID, SECOND_ID, 4, 2);

        assertThat(bodies(firstPage)).containsExactly("primero", "segundo");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(bodies(secondPage)).containsExactly("tercero", "quinto a");
        assertThat(secondPage.hasMore()).isTrue();
        assertThat(bodies(lastPage)).containsExactly("quinto b");
        assertThat(lastPage.hasMore()).isFalse();
    }

    @Test
    void anEmptyConversationReturnsAnEmptyPageForAParticipant() {
        MessagePage page = listMessages.execute(CONVERSATION_ID, SECOND_ID, 0, 20);

        assertThat(page.messages()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void deletedMessagesAreReadWithoutTheirBodyAndEditedOnesKeepTheirText() {
        insertMessage("00000000-0000-4000-8000-000000000001", FIRST_ID, "texto original", "SENT", "2026-11-07T15:01:00Z");
        jdbcClient.sql("""
                update public.messages
                set status = 'EDITED', body = 'texto corregido', edited_at = '2026-11-07T15:02:00Z'
                where id = '00000000-0000-4000-8000-000000000001'
                """).update();
        jdbcClient.sql("""
                insert into public.messages (id, conversation_id, sender_profile_id, body, status,
                                             created_at, deleted_at)
                values ('00000000-0000-4000-8000-000000000002', :conversationId, :sender,
                        'texto que debía ocultarse', 'DELETED', '2026-11-07T15:03:00Z',
                        '2026-11-07T15:04:00Z')
                """)
                .param("conversationId", CONVERSATION_ID)
                .param("sender", SECOND_ID)
                .update();

        List<Message> messages = listMessages.execute(CONVERSATION_ID, FIRST_ID, 0, 20).messages();

        assertThat(messages).extracting(Message::status)
                .containsExactly(MessageStatus.EDITED, MessageStatus.DELETED);
        assertThat(messages.get(0).body()).isEqualTo("texto corregido");
        assertThat(messages.get(0).editedAt()).isNotNull();
        assertThat(messages.get(1).body()).isNull();
        assertThat(messages.get(1).deletedAt()).isNotNull();
    }

    @Test
    void theDatabaseRejectsMessagesFromProfilesOutsideTheConversation() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                insert into public.messages (conversation_id, sender_profile_id, body)
                values (:conversationId, :sender, 'Intento directo')
                """)
                .param("conversationId", CONVERSATION_ID)
                .param("sender", OUTSIDER_ID)
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Message senders must be active participants");
        assertThat(messageCount()).isZero();
    }

    @Test
    void concurrentSendsAreAllPersistedAndLastMessageAtKeepsTheNewest() throws Exception {
        int senders = 8;
        ExecutorService executor = Executors.newFixedThreadPool(senders);
        try {
            List<Future<Message>> futures = new ArrayList<>();
            for (int index = 0; index < senders; index++) {
                UUID sender = index % 2 == 0 ? FIRST_ID : SECOND_ID;
                String body = "mensaje " + index;
                futures.add(executor.submit(() -> send(sender, body)));
            }
            List<Message> sent = new ArrayList<>();
            for (Future<Message> future : futures) {
                sent.add(future.get(30, TimeUnit.SECONDS));
            }

            OffsetDateTime newest = sent.stream()
                    .map(Message::createdAt)
                    .max(OffsetDateTime::compareTo)
                    .orElseThrow();
            OffsetDateTime lastMessageAt = jdbcClient
                    .sql("select last_message_at from public.conversations where id = :id")
                    .param("id", CONVERSATION_ID)
                    .query(OffsetDateTime.class)
                    .single();
            assertThat(messageCount()).isEqualTo(senders);
            assertThat(lastMessageAt).isEqualTo(newest);
        } finally {
            executor.shutdownNow();
        }
    }

    private Message send(UUID senderId, String body) {
        return transactionTemplate.execute(status ->
                sendTextMessage.execute(CONVERSATION_ID, senderId, body));
    }

    private void markAsLeft(UUID profileId) {
        jdbcClient.sql("""
                update public.conversation_participants
                set left_at = now()
                where conversation_id = :conversationId and profile_id = :profileId
                """)
                .param("conversationId", CONVERSATION_ID)
                .param("profileId", profileId)
                .update();
    }

    private void insertBlock(UUID blocker, UUID blocked) {
        jdbcClient.sql("insert into public.profile_blocks (blocker_profile_id, blocked_profile_id) values (:a, :b)")
                .param("a", blocker)
                .param("b", blocked)
                .update();
    }

    private void insertMessage(String id, UUID sender, String body, String status, String createdAt) {
        jdbcClient.sql("""
                insert into public.messages (id, conversation_id, sender_profile_id, body, status, created_at)
                values (cast(:id as uuid), :conversationId, :sender, :body, :status,
                        cast(:createdAt as timestamptz))
                """)
                .param("id", id)
                .param("conversationId", CONVERSATION_ID)
                .param("sender", sender)
                .param("body", body)
                .param("status", status)
                .param("createdAt", createdAt)
                .update();
    }

    private long messageCount() {
        return jdbcClient.sql("select count(*) from public.messages").query(Long.class).single();
    }

    private static List<String> bodies(MessagePage page) {
        return page.messages().stream().map(Message::body).toList();
    }

    private void applyMessagesMigration() throws SQLException, IOException {
        String script = Files.readString(MESSAGES_MIGRATION, StandardCharsets.UTF_8);
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
