package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.ProjectRepository;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;

@Repository
public class JdbcProjectRepository implements ProjectRepository {

    private final JdbcClient jdbcClient;

    public JdbcProjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean hasActiveSector(UUID sectorId) {
        return jdbcClient.sql("""
                select exists(
                    select 1
                    from public.sectors
                    where id = :sectorId
                      and is_active = true
                )
                """)
                .param("sectorId", sectorId)
                .query((resultSet, rowNumber) -> resultSet.getBoolean(1))
                .list()
                .stream()
                .findFirst()
                .orElse(false);
    }

    @Override
    public Project createDraft(ProjectDraft draft, UUID createdByProfileId) {
        return jdbcClient.sql("""
                insert into public.projects (
                    slug,
                    name,
                    tagline,
                    description,
                    sector_id,
                    city,
                    stage,
                    status,
                    created_by_profile_id
                ) values (
                    :slug,
                    :name,
                    :tagline,
                    :description,
                    :sectorId,
                    :city,
                    :stage,
                    'DRAFT',
                    :createdByProfileId
                )
                returning
                    id,
                    slug,
                    name,
                    tagline,
                    description,
                    sector_id,
                    city,
                    stage,
                    status,
                    created_by_profile_id
                """)
                .param("slug", draft.slug())
                .param("name", draft.name())
                .param("tagline", draft.tagline())
                .param("description", draft.description())
                .param("sectorId", draft.sectorId())
                .param("city", draft.city())
                .param("stage", draft.stage())
                .param("createdByProfileId", createdByProfileId)
                .query(JdbcProjectRepository::mapProject)
                .single();
    }

    @Override
    public ProjectMember createOwner(UUID projectId, UUID profileId) {
        return jdbcClient.sql("""
                insert into public.project_members (
                    project_id,
                    profile_id,
                    role,
                    membership_status,
                    created_by_profile_id,
                    joined_at
                ) values (
                    :projectId,
                    :profileId,
                    'OWNER',
                    'ACTIVE',
                    :createdByProfileId,
                    timezone('utc', now())
                )
                returning
                    id,
                    project_id,
                    profile_id,
                    role,
                    membership_status,
                    created_by_profile_id
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .param("createdByProfileId", profileId)
                .query(JdbcProjectRepository::mapProjectMember)
                .single();
    }

    private static Project mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Project(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tagline"),
                resultSet.getString("description"),
                resultSet.getObject("sector_id", UUID.class),
                resultSet.getString("city"),
                resultSet.getString("stage"),
                resultSet.getString("status"),
                resultSet.getObject("created_by_profile_id", UUID.class));
    }

    private static ProjectMember mapProjectMember(ResultSet resultSet, int rowNumber)
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
