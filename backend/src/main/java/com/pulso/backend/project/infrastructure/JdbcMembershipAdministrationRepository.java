package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.MembershipAdministrationRepository;
import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ProjectMember;

@Repository
public class JdbcMembershipAdministrationRepository implements MembershipAdministrationRepository {

    private final JdbcClient jdbcClient;

    public JdbcMembershipAdministrationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void lockProject(UUID projectId) {
        jdbcClient.sql("""
                select id
                from public.projects
                where id = :projectId
                for update
                """)
                .param("projectId", projectId)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .list();
    }

    @Override
    public Optional<MembershipActor> findActor(UUID projectId, UUID profileId) {
        return jdbcClient.sql("""
                select role, membership_status
                from public.project_members
                where project_id = :projectId
                  and profile_id = :profileId
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .query((resultSet, rowNumber) -> new MembershipActor(
                        projectId,
                        profileId,
                        resultSet.getString("role"),
                        resultSet.getString("membership_status")))
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProjectMember> findMember(UUID projectId, UUID memberId) {
        return jdbcClient.sql("""
                select id, project_id, profile_id, role, membership_status, created_by_profile_id
                from public.project_members
                where project_id = :projectId
                  and id = :memberId
                """)
                .param("projectId", projectId)
                .param("memberId", memberId)
                .query(JdbcMembershipAdministrationRepository::mapMember)
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public List<ProjectMember> findMembers(UUID projectId) {
        return jdbcClient.sql("""
                select id, project_id, profile_id, role, membership_status, created_by_profile_id
                from public.project_members
                where project_id = :projectId
                order by id
                """)
                .param("projectId", projectId)
                .query(JdbcMembershipAdministrationRepository::mapMember)
                .list();
    }

    @Override
    public int updateMembership(
            UUID projectId,
            UUID memberId,
            String expectedRole,
            String expectedMembershipStatus,
            String nextRole,
            String nextMembershipStatus) {
        return jdbcClient.sql("""
                update public.project_members
                set role = :nextRole,
                    membership_status = :nextMembershipStatus,
                    updated_at = timezone('utc', now())
                where project_id = :projectId
                  and id = :memberId
                  and role = :expectedRole
                  and membership_status = :expectedMembershipStatus
                """)
                .param("projectId", projectId)
                .param("memberId", memberId)
                .param("expectedRole", expectedRole)
                .param("expectedMembershipStatus", expectedMembershipStatus)
                .param("nextRole", nextRole)
                .param("nextMembershipStatus", nextMembershipStatus)
                .update();
    }

    private static ProjectMember mapMember(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ProjectMember(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("role"),
                resultSet.getString("membership_status"),
                resultSet.getObject("created_by_profile_id", UUID.class));
    }
}
