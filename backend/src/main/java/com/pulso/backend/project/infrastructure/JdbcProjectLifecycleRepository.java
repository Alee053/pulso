package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.ProjectLifecycleRepository;
import com.pulso.backend.project.domain.ProjectLifecycleActor;
import com.pulso.backend.project.domain.ProjectLifecycleSnapshot;
import com.pulso.backend.project.domain.ProjectLifecycleState;

@Repository
public class JdbcProjectLifecycleRepository implements ProjectLifecycleRepository {

    private static final String LIFECYCLE_FOR_ACTOR = """
            select
                p.id as project_id,
                p.status,
                p.published_at,
                p.archived_at,
                member.profile_id,
                member.role,
                member.membership_status
            from public.projects p
            join public.project_members member
              on member.project_id = p.id
            where p.id = :projectId
              and member.profile_id = :profileId
            """;

    private final JdbcClient jdbcClient;

    public JdbcProjectLifecycleRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ProjectLifecycleSnapshot> findForActor(UUID projectId, UUID profileId) {
        // Share membership administration's lock, then read a fresh permission snapshot.
        var lockedProject = jdbcClient.sql("""
                select p.id
                from public.projects p
                where p.id = :projectId
                  and exists (
                      select 1 from public.project_members member
                      where member.project_id = p.id and member.profile_id = :profileId
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

        return jdbcClient.sql(LIFECYCLE_FOR_ACTOR)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .query(JdbcProjectLifecycleRepository::mapSnapshot)
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public int updateLifecycle(
            UUID projectId,
            UUID profileId,
            String expectedStatus,
            ProjectLifecycleState nextState) {
        return jdbcClient.sql("""
                update public.projects
                set status = :nextStatus,
                    archived_at = :nextArchivedAt
                where id = :projectId
                  and status = :expectedStatus
                  and :nextStatus <> 'PUBLISHED'
                  and exists (
                      select 1
                      from public.project_members member
                      where member.project_id = public.projects.id
                        and member.profile_id = :profileId
                        and member.role in ('OWNER', 'ADMIN')
                        and member.membership_status = 'ACTIVE'
                  )
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .param("expectedStatus", expectedStatus)
                .param("nextStatus", nextState.status())
                .param("nextArchivedAt", asOffsetDateTime(nextState.archivedAt()))
                .update();
    }

    private static ProjectLifecycleSnapshot mapSnapshot(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ProjectLifecycleSnapshot(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                new ProjectLifecycleState(
                        resultSet.getString("status"),
                        asInstant(resultSet, "published_at"),
                        asInstant(resultSet, "archived_at")),
                new ProjectLifecycleActor(
                        resultSet.getString("role"),
                        resultSet.getString("membership_status")));
    }

    private static Instant asInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime asOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
