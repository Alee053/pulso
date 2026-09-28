package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.ProjectConflictException;
import com.pulso.backend.project.application.ProjectRepository;
import com.pulso.backend.project.application.ProjectUpdateRepository;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.UpdatedProject;

@Repository
public class JdbcProjectRepository implements ProjectRepository, ProjectUpdateRepository {

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
        try {
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
        } catch (DuplicateKeyException exception) {
            throw new ProjectConflictException();
        }
    }

    @Override
    public Optional<UpdatedProject> updateInformation(
            UUID projectId,
            UUID authenticatedProfileId,
            ProjectUpdatePatch patch) {
        return jdbcClient.sql("""
                update public.projects
                set name = coalesce(cast(:name as text), name),
                    tagline = coalesce(cast(:tagline as text), tagline),
                    description = coalesce(cast(:description as text), description),
                    value_proposition = coalesce(
                        cast(:valueProposition as text),
                        value_proposition),
                    stage = coalesce(cast(:stage as text), stage),
                    sector_id = coalesce(cast(:sectorId as uuid), sector_id),
                    city = coalesce(cast(:city as text), city),
                    department = coalesce(cast(:department as text), department)
                where id = :projectId
                  and exists (
                      select 1
                      from public.project_members member
                      where member.project_id = public.projects.id
                        and member.profile_id = :authenticatedProfileId
                        and member.role in ('OWNER', 'ADMIN')
                        and member.membership_status = 'ACTIVE'
                  )
                  and (
                      :sectorId is null
                      or exists (
                          select 1
                          from public.sectors sector
                          where sector.id = cast(:sectorId as uuid)
                            and sector.is_active = true
                      )
                  )
                returning
                    id,
                    slug,
                    name,
                    tagline,
                    description,
                    value_proposition,
                    sector_id,
                    stage,
                    city,
                    department,
                    status,
                    verification_status,
                    created_by_profile_id,
                    reviewed_by_profile_id,
                    review_note,
                    reviewed_at,
                    published_at,
                    created_at,
                    updated_at,
                    archived_at
                """)
                .param("name", patch.name())
                .param("tagline", patch.tagline())
                .param("description", patch.description())
                .param("valueProposition", patch.valueProposition())
                .param("stage", patch.stage())
                .param("sectorId", patch.sectorId())
                .param("city", patch.city())
                .param("department", patch.department())
                .param("projectId", projectId)
                .param("authenticatedProfileId", authenticatedProfileId)
                .query(JdbcProjectRepository::mapUpdatedProject)
                .list()
                .stream()
                .findFirst();
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

    private static UpdatedProject mapUpdatedProject(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new UpdatedProject(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tagline"),
                resultSet.getString("description"),
                resultSet.getString("value_proposition"),
                resultSet.getObject("sector_id", UUID.class),
                resultSet.getString("stage"),
                resultSet.getString("city"),
                resultSet.getString("department"),
                resultSet.getString("status"),
                resultSet.getString("verification_status"),
                resultSet.getObject("created_by_profile_id", UUID.class),
                resultSet.getObject("reviewed_by_profile_id", UUID.class),
                resultSet.getString("review_note"),
                resultSet.getObject("reviewed_at", java.time.OffsetDateTime.class),
                resultSet.getObject("published_at", java.time.OffsetDateTime.class),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class),
                resultSet.getObject("archived_at", java.time.OffsetDateTime.class));
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
