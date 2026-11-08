package com.pulso.backend.notification.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.notification.application.NotificationPublisher;
import com.pulso.backend.notification.application.NotificationRepository;
import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationReadFilter;
import com.pulso.backend.notification.domain.NotificationType;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcNotificationRepository implements NotificationRepository, NotificationPublisher {

    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {
    };
    private static final String COLUMNS = """
            id, recipient_profile_id, actor_profile_id, notification_type, title, body,
            project_id, conversation_id, connection_intent_id, cast(payload as text) as payload,
            read_at, created_at
            """;
    private static final String NOT_EXPIRED = "(expires_at is null or expires_at > now())";

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcNotificationRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Notification> findPage(
            UUID recipientProfileId,
            NotificationReadFilter filter,
            int offset,
            int limit) {
        // The predicate comes from a closed enum, never from client text, so each filter
        // keeps its own plan and can use the partial index for unread notifications.
        String readPredicate = switch (filter) {
            case ALL -> "true";
            case UNREAD -> "read_at is null";
            case READ -> "read_at is not null";
        };

        return jdbcClient.sql("select " + COLUMNS + """
                from public.notifications
                where recipient_profile_id = :recipientProfileId
                  and %s
                  and %s
                order by created_at desc, id desc
                offset :offset
                limit :limit
                """.formatted(readPredicate, NOT_EXPIRED))
                .param("recipientProfileId", recipientProfileId)
                .param("offset", offset)
                .param("limit", limit)
                .query(this::mapNotification)
                .list();
    }

    @Override
    public long countUnread(UUID recipientProfileId) {
        return jdbcClient.sql("""
                select count(*)
                from public.notifications
                where recipient_profile_id = :recipientProfileId
                  and read_at is null
                  and %s
                """.formatted(NOT_EXPIRED))
                .param("recipientProfileId", recipientProfileId)
                .query(Long.class)
                .single();
    }

    @Override
    public Optional<Notification> markRead(UUID notificationId, UUID recipientProfileId) {
        // Filtering by recipient makes another person's notification indistinguishable
        // from a missing one. The first read time is kept on repeated calls.
        return jdbcClient.sql("""
                update public.notifications
                set read_at = coalesce(read_at, timezone('utc', now()))
                where id = :notificationId
                  and recipient_profile_id = :recipientProfileId
                returning\s""" + COLUMNS)
                .param("notificationId", notificationId)
                .param("recipientProfileId", recipientProfileId)
                .query(this::mapNotification)
                .optional();
    }

    @Override
    public void connectionRequested(UUID connectionIntentId, UUID projectId, UUID senderProfileId) {
        jdbcClient.sql("""
                insert into public.notifications (
                    recipient_profile_id,
                    actor_profile_id,
                    project_id,
                    connection_intent_id,
                    notification_type,
                    title
                )
                select member.profile_id,
                       :senderProfileId,
                       :projectId,
                       :connectionIntentId,
                       :type,
                       :title
                from public.project_members member
                where member.project_id = :projectId
                  and member.membership_status = 'ACTIVE'
                  and member.profile_id is not null
                  and member.profile_id <> :senderProfileId
                  and not exists (
                      select 1
                      from public.profile_blocks block
                      where (block.blocker_profile_id = member.profile_id
                             and block.blocked_profile_id = :senderProfileId)
                         or (block.blocker_profile_id = :senderProfileId
                             and block.blocked_profile_id = member.profile_id)
                  )
                on conflict do nothing
                """)
                .param("senderProfileId", senderProfileId)
                .param("projectId", projectId)
                .param("connectionIntentId", connectionIntentId)
                .param("type", NotificationType.CONNECTION_REQUEST.name())
                .param("title", NotificationType.CONNECTION_REQUEST.title())
                .update();
    }

    @Override
    public void connectionAccepted(
            UUID connectionIntentId,
            UUID projectId,
            UUID conversationId,
            UUID initiatorProfileId,
            UUID responderProfileId) {
        jdbcClient.sql("""
                insert into public.notifications (
                    recipient_profile_id,
                    actor_profile_id,
                    project_id,
                    conversation_id,
                    connection_intent_id,
                    notification_type,
                    title,
                    payload
                ) values (
                    :initiatorProfileId,
                    :responderProfileId,
                    :projectId,
                    :conversationId,
                    :connectionIntentId,
                    :type,
                    :title,
                    jsonb_build_object('response', 'ACCEPTED')
                )
                on conflict do nothing
                """)
                .param("initiatorProfileId", initiatorProfileId)
                .param("responderProfileId", responderProfileId)
                .param("projectId", projectId)
                .param("conversationId", conversationId)
                .param("connectionIntentId", connectionIntentId)
                .param("type", NotificationType.CONNECTION_RESPONSE.name())
                .param("title", NotificationType.CONNECTION_RESPONSE.title())
                .update();
    }

    @Override
    public void messageSent(UUID conversationId, UUID messageId, UUID senderProfileId) {
        // Only identifiers are stored: the text stays in messages, so a deleted message
        // leaves no copy behind in a notification.
        jdbcClient.sql("""
                insert into public.notifications (
                    recipient_profile_id,
                    actor_profile_id,
                    project_id,
                    conversation_id,
                    notification_type,
                    title,
                    payload
                )
                select participant.profile_id,
                       :senderProfileId,
                       conversation.project_id,
                       conversation.id,
                       :type,
                       :title,
                       jsonb_build_object('messageId', cast(:messageId as text))
                from public.conversation_participants participant
                join public.conversations conversation on conversation.id = participant.conversation_id
                where participant.conversation_id = :conversationId
                  and participant.left_at is null
                  and participant.profile_id <> :senderProfileId
                """)
                .param("senderProfileId", senderProfileId)
                .param("conversationId", conversationId)
                .param("messageId", messageId)
                .param("type", NotificationType.NEW_MESSAGE.name())
                .param("title", NotificationType.NEW_MESSAGE.title())
                .update();
    }

    private Notification mapNotification(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Notification(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("recipient_profile_id", UUID.class),
                resultSet.getObject("actor_profile_id", UUID.class),
                NotificationType.valueOf(resultSet.getString("notification_type")),
                resultSet.getString("title"),
                resultSet.getString("body"),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("conversation_id", UUID.class),
                resultSet.getObject("connection_intent_id", UUID.class),
                readPayload(resultSet.getString("payload")),
                resultSet.getObject("read_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private Map<String, String> readPayload(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored notification payload is not valid JSON.", exception);
        }
    }
}
