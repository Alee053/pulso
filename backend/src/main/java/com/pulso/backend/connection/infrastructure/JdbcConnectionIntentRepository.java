package com.pulso.backend.connection.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.application.ConnectionIntentRejectedException;
import com.pulso.backend.connection.application.ConnectionIntentRejectedException.Reason;
import com.pulso.backend.connection.application.ConnectionIntentRepository;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;

@Repository
public class JdbcConnectionIntentRepository implements ConnectionIntentRepository {

    private static final String PROJECT_CONTEXT_UNIQUE_INDEX =
            "connection_intents_pending_project_context_uidx";
    private static final String OPPORTUNITY_CONTEXT_UNIQUE_INDEX =
            "connection_intents_pending_opportunity_context_uidx";

    private final JdbcClient jdbcClient;

    public JdbcConnectionIntentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public ConnectionIntent createPending(
            UUID senderProfileId,
            UUID projectId,
            UUID opportunityId,
            ConnectionIntentReason reason,
            String message) {
        if (!lockActiveSender(senderProfileId)) {
            throw new ConnectionIntentRejectedException(Reason.PROFILE_INACTIVE);
        }
        if (!lockPublicProject(projectId)) {
            throw new ConnectionIntentRejectedException(Reason.CONTEXT_UNAVAILABLE);
        }
        if (opportunityId != null && !lockOpenOpportunity(projectId, opportunityId)) {
            throw new ConnectionIntentRejectedException(Reason.CONTEXT_UNAVAILABLE);
        }

        try {
            return jdbcClient.sql("""
                    insert into public.connection_intents (
                        from_profile_id,
                        project_id,
                        opportunity_id,
                        reason,
                        message
                    ) values (
                        :senderProfileId,
                        :projectId,
                        cast(:opportunityId as uuid),
                        :reason,
                        :message
                    )
                    returning
                        id,
                        from_profile_id,
                        project_id,
                        opportunity_id,
                        reason,
                        message,
                        status,
                        created_at
                    """)
                    .param("senderProfileId", senderProfileId)
                    .param("projectId", projectId)
                    .param("opportunityId", opportunityId)
                    .param("reason", reason.name())
                    .param("message", normalizeOptionalMessage(message))
                    .query(JdbcConnectionIntentRepository::mapConnectionIntent)
                    .single();
        } catch (DuplicateKeyException exception) {
            if (isPendingContextUniqueViolation(exception)) {
                throw new ConnectionIntentRejectedException(Reason.DUPLICATE_PENDING);
            }
            throw exception;
        }
    }

    private boolean lockActiveSender(UUID senderProfileId) {
        return jdbcClient.sql("""
                select id
                from public.profiles
                where id = :senderProfileId
                  and account_status = 'ACTIVE'
                  and deleted_at is null
                for share
                """)
                .param("senderProfileId", senderProfileId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    private boolean lockPublicProject(UUID projectId) {
        return jdbcClient.sql("""
                select project.id
                from public.projects project
                join public.sectors sector on sector.id = project.sector_id
                where project.id = :projectId
                  and project.status = 'PUBLISHED'
                  and sector.is_active = true
                for share of project, sector
                """)
                .param("projectId", projectId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    private boolean lockOpenOpportunity(UUID projectId, UUID opportunityId) {
        return jdbcClient.sql("""
                select id
                from public.project_opportunities
                where id = :opportunityId
                  and project_id = :projectId
                  and status = 'OPEN'
                for share
                """)
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    private static ConnectionIntent mapConnectionIntent(ResultSet resultSet, int rowNumber)
            throws SQLException {
        String persistedStatus = resultSet.getString("status");
        if (!"PENDING".equals(persistedStatus)) {
            throw new SQLException("New connection intents must be persisted as PENDING.");
        }
        OffsetDateTime timestamp = resultSet.getObject("created_at", OffsetDateTime.class);
        return ConnectionIntent.pending(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("from_profile_id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("opportunity_id", UUID.class),
                ConnectionIntentReason.parse(resultSet.getString("reason")),
                resultSet.getString("message"),
                timestamp);
    }

    private static String normalizeOptionalMessage(String message) {
        if (message == null) {
            return null;
        }
        String normalized = message.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isPendingContextUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                String message = sqlException.getMessage();
                return message != null
                        && (message.contains(PROJECT_CONTEXT_UNIQUE_INDEX)
                        || message.contains(OPPORTUNITY_CONTEXT_UNIQUE_INDEX));
            }
        }
        return false;
    }
}
