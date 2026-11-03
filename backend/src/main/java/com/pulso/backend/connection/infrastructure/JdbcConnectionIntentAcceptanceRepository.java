package com.pulso.backend.connection.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.connection.application.ConnectionIntentAcceptanceRepository;
import com.pulso.backend.connection.application.ConnectionIntentConflictException;
import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;

@Repository
public class JdbcConnectionIntentAcceptanceRepository
        implements ConnectionIntentAcceptanceRepository {

    private final JdbcClient jdbcClient;

    public JdbcConnectionIntentAcceptanceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ConnectionIntent> findByIdForUpdate(UUID connectionIntentId) {
        return jdbcClient.sql("""
                select id,
                       from_profile_id,
                       project_id,
                       opportunity_id,
                       reason,
                       message,
                       status,
                       responded_by_profile_id,
                       created_at
                from public.connection_intents
                where id = :connectionIntentId
                for update
                """)
                .param("connectionIntentId", connectionIntentId)
                .query(JdbcConnectionIntentAcceptanceRepository::mapConnectionIntent)
                .optional();
    }

    @Override
    public boolean hasActiveProjectMembership(UUID projectId, UUID profileId) {
        return jdbcClient.sql("""
                select membership_status
                from public.project_members
                where project_id = :projectId
                  and profile_id = :profileId
                for update
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .query(String.class)
                .optional()
                .filter("ACTIVE"::equals)
                .isPresent();
    }

    @Override
    public boolean profilesAreBlocked(UUID firstProfileId, UUID secondProfileId) {
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

    @Override
    public Optional<AcceptedConnectionConversation> findConversation(UUID connectionIntentId) {
        return jdbcClient.sql("""
                select c.connection_intent_id,
                       c.id as conversation_id,
                       c.project_id,
                       c.status,
                       i.from_profile_id,
                       i.responded_by_profile_id
                from public.conversations c
                join public.connection_intents i on i.id = c.connection_intent_id
                where c.connection_intent_id = :connectionIntentId
                  and c.conversation_type = 'PROJECT_CONTACT'
                """)
                .param("connectionIntentId", connectionIntentId)
                .query(JdbcConnectionIntentAcceptanceRepository::mapConversation)
                .optional();
    }

    @Override
    public AcceptedConnectionConversation acceptAndCreateConversation(
            ConnectionIntent connectionIntent,
            UUID responderProfileId) {
        int updated = jdbcClient.sql("""
                update public.connection_intents
                set status = 'ACCEPTED',
                    responded_by_profile_id = :responderProfileId,
                    responded_at = timezone('utc', now())
                where id = :connectionIntentId
                  and status = 'PENDING'
                  and responded_by_profile_id is null
                """)
                .param("responderProfileId", responderProfileId)
                .param("connectionIntentId", connectionIntent.id())
                .update();
        if (updated != 1) {
            throw new ConnectionIntentConflictException();
        }

        UUID conversationId = jdbcClient.sql("""
                insert into public.conversations (
                    conversation_type,
                    project_id,
                    connection_intent_id,
                    created_by_profile_id,
                    status
                ) values (
                    'PROJECT_CONTACT',
                    :projectId,
                    :connectionIntentId,
                    :initiatorProfileId,
                    'ACTIVE'
                )
                returning id
                """)
                .param("projectId", connectionIntent.projectId())
                .param("connectionIntentId", connectionIntent.id())
                .param("initiatorProfileId", connectionIntent.fromProfileId())
                .query(UUID.class)
                .single();

        int participants = jdbcClient.sql("""
                insert into public.conversation_participants (
                    conversation_id,
                    profile_id,
                    participant_role
                ) values
                    (:conversationId, :initiatorProfileId, 'INITIATOR'),
                    (:conversationId, :responderProfileId, 'PROJECT_MEMBER')
                """)
                .param("conversationId", conversationId)
                .param("initiatorProfileId", connectionIntent.fromProfileId())
                .param("responderProfileId", responderProfileId)
                .update();
        if (participants != 2) {
            throw new ConnectionIntentConflictException();
        }

        return new AcceptedConnectionConversation(
                connectionIntent.id(),
                conversationId,
                connectionIntent.projectId(),
                connectionIntent.fromProfileId(),
                responderProfileId,
                "ACTIVE");
    }

    private static ConnectionIntent mapConnectionIntent(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return ConnectionIntent.restored(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("from_profile_id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("opportunity_id", UUID.class),
                ConnectionIntentReason.parse(resultSet.getString("reason")),
                resultSet.getString("message"),
                resultSet.getString("status"),
                resultSet.getObject("responded_by_profile_id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private static AcceptedConnectionConversation mapConversation(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new AcceptedConnectionConversation(
                resultSet.getObject("connection_intent_id", UUID.class),
                resultSet.getObject("conversation_id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("from_profile_id", UUID.class),
                resultSet.getObject("responded_by_profile_id", UUID.class),
                resultSet.getString("status"));
    }
}
