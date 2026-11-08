package com.pulso.backend.messaging.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.messaging.application.ConversationMessageRepository;
import com.pulso.backend.messaging.domain.ConversationAccess;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageStatus;
import com.pulso.backend.messaging.domain.MessageType;

@Repository
public class JdbcConversationMessageRepository implements ConversationMessageRepository {

    private final JdbcClient jdbcClient;

    public JdbcConversationMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ConversationAccess> lockForActiveParticipant(UUID conversationId, UUID profileId) {
        // The conversation row serializes sends and status changes; the participant row
        // cannot leave the conversation until this transaction ends.
        return jdbcClient.sql("""
                select c.id, c.status
                from public.conversations c
                join public.conversation_participants p on p.conversation_id = c.id
                where c.id = :conversationId
                  and p.profile_id = :profileId
                  and p.left_at is null
                for update of c
                for share of p
                """)
                .param("conversationId", conversationId)
                .param("profileId", profileId)
                .query((resultSet, rowNumber) -> new ConversationAccess(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("status")))
                .optional();
    }

    @Override
    public boolean isBlockedFromParticipants(UUID conversationId, UUID profileId) {
        List<UUID> counterparts = jdbcClient.sql("""
                select profile_id
                from public.conversation_participants
                where conversation_id = :conversationId
                  and profile_id <> :profileId
                  and left_at is null
                order by profile_id
                """)
                .param("conversationId", conversationId)
                .param("profileId", profileId)
                .query(UUID.class)
                .list();

        for (UUID counterpart : counterparts) {
            lockContactPair(profileId, counterpart);
            if (profilesAreBlocked(profileId, counterpart)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Message appendText(UUID conversationId, UUID senderProfileId, String body) {
        Message message = jdbcClient.sql("""
                insert into public.messages (
                    conversation_id,
                    sender_profile_id,
                    body,
                    message_type,
                    status
                ) values (
                    :conversationId,
                    :senderProfileId,
                    :body,
                    'TEXT',
                    'SENT'
                )
                returning id, conversation_id, sender_profile_id, message_type, status,
                          body, created_at, edited_at, deleted_at
                """)
                .param("conversationId", conversationId)
                .param("senderProfileId", senderProfileId)
                .param("body", body)
                .query(JdbcConversationMessageRepository::mapMessage)
                .single();

        jdbcClient.sql("""
                update public.conversations
                set last_message_at = greatest(
                    coalesce(last_message_at, :createdAt),
                    :createdAt
                )
                where id = :conversationId
                """)
                .param("createdAt", message.createdAt())
                .param("conversationId", conversationId)
                .update();

        return message;
    }

    @Override
    public Optional<List<Message>> findPageForActiveParticipant(
            UUID conversationId,
            UUID profileId,
            int offset,
            int limit) {
        // One statement gives authorization and page contents the same PostgreSQL snapshot.
        // The left lateral join keeps an authorized participant when the page is empty.
        List<Message> rows = jdbcClient.sql("""
                with actor as (
                    select p.profile_id
                    from public.conversation_participants p
                    where p.conversation_id = :conversationId
                      and p.profile_id = :profileId
                      and p.left_at is null
                )
                select page.id, page.conversation_id, page.sender_profile_id, page.message_type,
                       page.status, page.body, page.created_at, page.edited_at, page.deleted_at
                from actor
                left join lateral (
                    select m.id, m.conversation_id, m.sender_profile_id, m.message_type,
                           m.status,
                           case when m.status = 'DELETED' then null else m.body end as body,
                           m.created_at, m.edited_at, m.deleted_at
                    from public.messages m
                    where m.conversation_id = :conversationId
                    order by m.created_at, m.id
                    offset :offset
                    limit :limit
                ) page on true
                order by page.created_at, page.id
                """)
                .param("conversationId", conversationId)
                .param("profileId", profileId)
                .param("offset", offset)
                .param("limit", limit)
                .query(JdbcConversationMessageRepository::mapNullableMessage)
                .list();

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.stream().filter(message -> message != null).toList());
    }

    private void lockContactPair(UUID firstProfileId, UUID secondProfileId) {
        jdbcClient.sql("""
                select true
                from (
                    select pg_advisory_xact_lock(hashtextextended(
                        concat_ws(
                            ':',
                            least(
                                cast(:firstProfileId as text),
                                cast(:secondProfileId as text)
                            ),
                            greatest(
                                cast(:firstProfileId as text),
                                cast(:secondProfileId as text)
                            )
                        ),
                        0
                    ))
                ) contact_lock
                """)
                .param("firstProfileId", firstProfileId)
                .param("secondProfileId", secondProfileId)
                .query(Boolean.class)
                .single();
    }

    private boolean profilesAreBlocked(UUID firstProfileId, UUID secondProfileId) {
        return jdbcClient.sql("""
                select exists(
                           select 1
                           from public.profile_blocks
                           where (blocker_profile_id = :firstProfileId
                                  and blocked_profile_id = :secondProfileId)
                              or (blocker_profile_id = :secondProfileId
                                  and blocked_profile_id = :firstProfileId)
                       )
                """)
                .param("firstProfileId", firstProfileId)
                .param("secondProfileId", secondProfileId)
                .query(Boolean.class)
                .single();
    }

    private static Message mapNullableMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        if (resultSet.getObject("id", UUID.class) == null) {
            return null;
        }
        return mapMessage(resultSet, rowNumber);
    }

    private static Message mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Message(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("conversation_id", UUID.class),
                resultSet.getObject("sender_profile_id", UUID.class),
                MessageType.valueOf(resultSet.getString("message_type")),
                MessageStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("body"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("edited_at", OffsetDateTime.class),
                resultSet.getObject("deleted_at", OffsetDateTime.class));
    }
}
