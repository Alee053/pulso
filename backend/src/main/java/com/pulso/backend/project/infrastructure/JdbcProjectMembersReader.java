package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.ProjectMembersReader;
import com.pulso.backend.project.application.ProjectMembersSnapshot;
import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ProjectMemberSummary;

@Repository
public class JdbcProjectMembersReader implements ProjectMembersReader {

    private final JdbcClient jdbcClient;

    public JdbcProjectMembersReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ProjectMembersSnapshot> findAuthorizedPage(UUID projectId, UUID profileId, int offset, int limit) {
        // One statement gives authorization and page contents the same PostgreSQL snapshot.
        // The left lateral join preserves an authorized actor when its page is empty.
        List<AuthorizedRow> rows = jdbcClient.sql("""
                with actor as (
                    select role, membership_status
                    from public.project_members
                    where project_id = :projectId
                      and profile_id = :profileId
                      and membership_status = 'ACTIVE'
                      and role in ('OWNER', 'ADMIN', 'MEMBER')
                )
                select actor.role as actor_role, actor.membership_status as actor_status,
                       page.id, page.project_id, page.profile_id, page.role, page.membership_status,
                       page.display_name, page.display_title
                from actor
                left join lateral (
                    select visible.id, visible.project_id, visible.profile_id,
                           visible.role, visible.membership_status,
                           case when visible.is_visible then visible.display_name end as display_name,
                           case when visible.is_visible then visible.display_title end as display_title
                    from (
                        select m.id, m.project_id, m.profile_id, m.role, m.membership_status,
                               p.display_name, m.display_title,
                               (m.membership_status = 'ACTIVE' and m.is_public = true
                                and p.account_status = 'ACTIVE' and p.profile_visibility = 'PUBLIC'
                                and p.deleted_at is null) as is_visible
                        from public.project_members m
                        left join public.profiles p on p.id = m.profile_id
                        where m.project_id = :projectId
                    ) visible
                    where actor.role in ('OWNER', 'ADMIN') or visible.is_visible
                    order by visible.id
                    offset :offset
                    limit :limit
                ) page on true
                order by page.id
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .param("offset", offset)
                .param("limit", limit)
                .query(JdbcProjectMembersReader::mapRow)
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AuthorizedRow first = rows.getFirst();
        MembershipActor actor = new MembershipActor(projectId, profileId, first.actorRole(), first.actorStatus());
        return Optional.of(new ProjectMembersSnapshot(actor,
                rows.stream().map(AuthorizedRow::member).filter(member -> member != null).toList()));
    }

    private static AuthorizedRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID memberId = resultSet.getObject("id", UUID.class);
        ProjectMemberSummary member = memberId == null ? null : new ProjectMemberSummary(
                memberId,
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("role"),
                resultSet.getString("membership_status"),
                resultSet.getString("display_name"),
                resultSet.getString("display_title"));
        return new AuthorizedRow(resultSet.getString("actor_role"), resultSet.getString("actor_status"), member);
    }

    private record AuthorizedRow(String actorRole, String actorStatus, ProjectMemberSummary member) {
    }
}
