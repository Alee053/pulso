package com.pulso.backend.report.infrastructure;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.report.application.ReportRepository;
import com.pulso.backend.report.domain.ReportReceipt;
import com.pulso.backend.report.domain.ReportTarget;

@Repository
public class JdbcReportRepository implements ReportRepository {
    private final JdbcClient jdbc;

    public JdbcReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReportReceipt> insertIfAccessible(
            UUID reporterId, ReportTarget target, String reason, String description) {
        JdbcClient.StatementSpec insert = jdbc.sql("""
                insert into public.reports
                    (reporter_profile_id, target_profile_id, target_project_id, target_message_id, reason, description)
                select actor.id, cast(:profileId as uuid), cast(:projectId as uuid),
                       cast(:messageId as uuid), :reason, :description
                from public.profiles actor
                where actor.id = :reporterId and actor.account_status = 'ACTIVE' and actor.deleted_at is null
                  and (
                    exists (
                        select 1 from public.profiles target
                        where target.id = :profileId and target.account_status = 'ACTIVE' and target.deleted_at is null
                          and (target.profile_visibility = 'PUBLIC' or target.id = actor.id)
                    )
                    or exists (
                        select 1 from public.projects project
                        join public.sectors sector on sector.id = project.sector_id
                        where project.id = :projectId
                          and ((project.status = 'PUBLISHED' and sector.is_active) or exists (
                              select 1 from public.project_members member
                              where member.project_id = project.id and member.profile_id = actor.id
                                and member.membership_status = 'ACTIVE'
                          ))
                    )
                    or exists (
                        select 1 from public.messages message
                        join public.conversation_participants participant
                          on participant.conversation_id = message.conversation_id
                        where message.id = :messageId and participant.profile_id = actor.id
                          and participant.left_at is null
                    )
                  )
                returning id, status, created_at
                """)
                .param("reporterId", reporterId)
                .param("profileId", target.profileId(), Types.OTHER)
                .param("projectId", target.projectId(), Types.OTHER)
                .param("messageId", target.messageId(), Types.OTHER)
                .param("reason", reason)
                .param("description", description, Types.VARCHAR);
        try {
            return insert.query((rs, rowNum) -> new ReportReceipt(
                            rs.getObject("id", UUID.class),
                            rs.getString("status"),
                            rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                    .optional();
        } catch (DataAccessException exception) {
            // PostgreSQL can include the submitted text in diagnostic row details.
            throw new ReportPersistenceException();
        }
    }

    private static class ReportPersistenceException extends DataAccessException {
        ReportPersistenceException() {
            super("Unable to store a report.");
        }
    }
}
