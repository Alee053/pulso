package com.pulso.backend.opportunity.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.opportunity.application.ProjectOpportunityRepository;
import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

@Repository
public class JdbcProjectOpportunityRepository implements ProjectOpportunityRepository {

    private static final String OPPORTUNITY_COLUMNS = """
            id,
            project_id,
            opportunity_type,
            title,
            description,
            status,
            city,
            department,
            remote_allowed,
            created_by_profile_id,
            starts_at,
            ends_at,
            closed_at,
            created_at,
            updated_at
            """;

    private final JdbcClient jdbcClient;

    public JdbcProjectOpportunityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<OpportunityActor> lockActor(UUID projectId, UUID profileId) {
        Optional<UUID> lockedProject = jdbcClient.sql("""
                select project.id
                from public.projects project
                where project.id = :projectId
                  and exists (
                      select 1
                      from public.project_members member
                      where member.project_id = project.id
                        and member.profile_id = :profileId
                  )
                for update
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .query(UUID.class)
                .optional();
        if (lockedProject.isEmpty()) {
            return Optional.empty();
        }

        // Re-read membership after acquiring the same project lock used by team
        // administration so a concurrent revocation cannot authorize a stale actor.
        return jdbcClient.sql("""
                select
                    project.id as project_id,
                    project.status as project_status,
                    project.sector_id,
                    member.profile_id,
                    member.role,
                    member.membership_status
                from public.projects project
                join public.project_members member on member.project_id = project.id
                where project.id = :projectId
                  and member.profile_id = :profileId
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .query((resultSet, rowNumber) -> new OpportunityActor(
                        resultSet.getObject("project_id", UUID.class),
                        resultSet.getObject("profile_id", UUID.class),
                        resultSet.getString("project_status"),
                        resultSet.getObject("sector_id", UUID.class),
                        resultSet.getString("role"),
                        resultSet.getString("membership_status")))
                .optional();
    }

    @Override
    public boolean lockActiveSector(UUID sectorId) {
        if (sectorId == null) {
            return false;
        }
        return jdbcClient.sql("""
                select id
                from public.sectors
                where id = :sectorId and is_active = true
                for share
                """)
                .param("sectorId", sectorId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    @Override
    public List<ProjectOpportunity> list(UUID projectId, int offset, int limit) {
        return jdbcClient.sql("""
                select %s
                from public.project_opportunities
                where project_id = :projectId
                order by created_at desc, id desc
                limit :limit offset :offset
                """.formatted(OPPORTUNITY_COLUMNS))
                .param("projectId", projectId)
                .param("limit", limit)
                .param("offset", offset)
                .query(JdbcProjectOpportunityRepository::mapOpportunity)
                .list();
    }

    @Override
    public Optional<ProjectOpportunity> find(UUID projectId, UUID opportunityId) {
        return jdbcClient.sql("""
                select %s
                from public.project_opportunities
                where project_id = :projectId and id = :opportunityId
                """.formatted(OPPORTUNITY_COLUMNS))
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .query(JdbcProjectOpportunityRepository::mapOpportunity)
                .optional();
    }

    @Override
    public Optional<ProjectOpportunity> create(
            UUID projectId,
            UUID profileId,
            OpportunityContent content,
            String status) {
        return jdbcClient.sql("""
                insert into public.project_opportunities (
                    project_id,
                    opportunity_type,
                    title,
                    description,
                    status,
                    city,
                    department,
                    remote_allowed,
                    created_by_profile_id
                )
                select
                    :projectId,
                    :opportunityType,
                    :title,
                    :description,
                    :status,
                    :city,
                    :department,
                    :remoteAllowed,
                    :profileId
                where exists (
                    select 1
                    from public.project_members member
                    where member.project_id = :projectId
                      and member.profile_id = :profileId
                      and member.role in ('OWNER', 'ADMIN')
                      and member.membership_status = 'ACTIVE'
                )
                returning %s
                """.formatted(OPPORTUNITY_COLUMNS))
                .param("projectId", projectId)
                .param("opportunityType", content.opportunityType())
                .param("title", content.title())
                .param("description", content.description())
                .param("status", status)
                .param("city", content.city())
                .param("department", content.department())
                .param("remoteAllowed", content.remoteAllowed())
                .param("profileId", profileId)
                .query(JdbcProjectOpportunityRepository::mapOpportunity)
                .optional();
    }

    @Override
    public Optional<ProjectOpportunity> updateContent(
            UUID projectId,
            UUID opportunityId,
            UUID profileId,
            OffsetDateTime expectedUpdatedAt,
            OpportunityContent content) {
        return jdbcClient.sql("""
                update public.project_opportunities opportunity
                set opportunity_type = :opportunityType,
                    title = :title,
                    description = :description,
                    city = :city,
                    department = :department,
                    remote_allowed = :remoteAllowed
                where opportunity.project_id = :projectId
                  and opportunity.id = :opportunityId
                  and opportunity.status <> 'ARCHIVED'
                  and opportunity.updated_at = :expectedUpdatedAt
                  and exists (
                      select 1
                      from public.project_members member
                      where member.project_id = opportunity.project_id
                        and member.profile_id = :profileId
                        and member.role in ('OWNER', 'ADMIN')
                        and member.membership_status = 'ACTIVE'
                  )
                returning %s
                """.formatted(OPPORTUNITY_COLUMNS))
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .param("profileId", profileId)
                .param("expectedUpdatedAt", expectedUpdatedAt)
                .param("opportunityType", content.opportunityType())
                .param("title", content.title())
                .param("description", content.description())
                .param("city", content.city())
                .param("department", content.department())
                .param("remoteAllowed", content.remoteAllowed())
                .query(JdbcProjectOpportunityRepository::mapOpportunity)
                .optional();
    }

    @Override
    public Optional<ProjectOpportunity> updateStatus(
            UUID projectId,
            UUID opportunityId,
            UUID profileId,
            String expectedStatus,
            OffsetDateTime expectedUpdatedAt,
            String nextStatus,
            OffsetDateTime closedAt) {
        return jdbcClient.sql("""
                update public.project_opportunities opportunity
                set status = :nextStatus,
                    closed_at = case
                        when :nextStatus = 'CLOSED' then coalesce(opportunity.closed_at, :closedAt)
                        else opportunity.closed_at
                    end
                where opportunity.project_id = :projectId
                  and opportunity.id = :opportunityId
                  and opportunity.status = :expectedStatus
                  and opportunity.updated_at = :expectedUpdatedAt
                  and exists (
                      select 1
                      from public.project_members member
                      where member.project_id = opportunity.project_id
                        and member.profile_id = :profileId
                        and member.role in ('OWNER', 'ADMIN')
                        and member.membership_status = 'ACTIVE'
                  )
                returning %s
                """.formatted(OPPORTUNITY_COLUMNS))
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .param("profileId", profileId)
                .param("expectedStatus", expectedStatus)
                .param("expectedUpdatedAt", expectedUpdatedAt)
                .param("nextStatus", nextStatus)
                .param("closedAt", closedAt)
                .query(JdbcProjectOpportunityRepository::mapOpportunity)
                .optional();
    }

    private static ProjectOpportunity mapOpportunity(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ProjectOpportunity(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("opportunity_type"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("status"),
                resultSet.getString("city"),
                resultSet.getString("department"),
                resultSet.getBoolean("remote_allowed"),
                resultSet.getObject("created_by_profile_id", UUID.class),
                resultSet.getObject("starts_at", OffsetDateTime.class),
                resultSet.getObject("ends_at", OffsetDateTime.class),
                resultSet.getObject("closed_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }
}
