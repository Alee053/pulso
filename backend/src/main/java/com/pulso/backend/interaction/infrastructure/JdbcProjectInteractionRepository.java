package com.pulso.backend.interaction.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.interaction.application.ProjectInteractionRepository;
import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.application.PublicProjectNotFoundException;

@Repository
public class JdbcProjectInteractionRepository implements ProjectInteractionRepository {

    private static final String PROJECT_COLUMNS = """
            p.id,
            p.slug,
            p.name,
            p.tagline,
            s.name as sector,
            coalesce(p.city, '') as city,
            p.stage
            """;

    private final JdbcClient jdbcClient;

    public JdbcProjectInteractionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<DiscoveryFeedItem> findDiscoveryFeed(UUID profileId, int offset, int limit) {
        return jdbcClient.sql("""
                select %s,
                       (sp.project_id is not null) as saved,
                       pf.feedback_type
                from public.projects p
                join public.sectors s on s.id = p.sector_id
                left join public.saved_projects sp
                  on sp.project_id = p.id
                 and sp.profile_id = :profileId
                 and sp.unsaved_at is null
                left join public.project_feedback pf
                  on pf.project_id = p.id
                 and pf.profile_id = :profileId
                where p.status = 'PUBLISHED'
                  and coalesce(pf.feedback_type, 'NONE') not in ('INTERESTED', 'DISMISSED')
                order by p.published_at desc, p.id desc
                offset :offset
                limit :limit
                """.formatted(PROJECT_COLUMNS))
                .param("profileId", profileId)
                .param("offset", offset)
                .param("limit", limit)
                .query(JdbcProjectInteractionRepository::mapFeedItem)
                .list();
    }

    @Override
    public long countDiscoveryFeed(UUID profileId) {
        return jdbcClient.sql("""
                select count(*)
                from public.projects p
                where p.status = 'PUBLISHED'
                  and not exists (
                      select 1
                      from public.project_feedback pf
                      where pf.profile_id = :profileId
                        and pf.project_id = p.id
                        and pf.feedback_type in ('INTERESTED', 'DISMISSED')
                  )
                """)
                .param("profileId", profileId)
                .query(Long.class)
                .single();
    }

    @Override
    public List<ProjectSummary> findSavedProjects(UUID profileId, int offset, int limit) {
        return jdbcClient.sql("""
                select %s
                from public.saved_projects saved
                join public.projects p on p.id = saved.project_id
                join public.sectors s on s.id = p.sector_id
                where saved.profile_id = :profileId
                  and saved.unsaved_at is null
                  and p.status = 'PUBLISHED'
                order by saved.saved_at desc, p.id desc
                offset :offset
                limit :limit
                """.formatted(PROJECT_COLUMNS))
                .param("profileId", profileId)
                .param("offset", offset)
                .param("limit", limit)
                .query(JdbcProjectInteractionRepository::mapProjectSummary)
                .list();
    }

    @Override
    public long countSavedProjects(UUID profileId) {
        return jdbcClient.sql("""
                select count(*)
                from public.saved_projects saved
                join public.projects p on p.id = saved.project_id
                where saved.profile_id = :profileId
                  and saved.unsaved_at is null
                  and p.status = 'PUBLISHED'
                """)
                .param("profileId", profileId)
                .query(Long.class)
                .single();
    }

    @Override
    public List<ProjectSummary> findInterestedProjects(UUID profileId, int offset, int limit) {
        return jdbcClient.sql("""
                select %s
                from public.project_feedback feedback
                join public.projects p on p.id = feedback.project_id
                join public.sectors s on s.id = p.sector_id
                where feedback.profile_id = :profileId
                  and feedback.feedback_type = 'INTERESTED'
                  and p.status = 'PUBLISHED'
                order by feedback.changed_at desc, p.id desc
                offset :offset
                limit :limit
                """.formatted(PROJECT_COLUMNS))
                .param("profileId", profileId)
                .param("offset", offset)
                .param("limit", limit)
                .query(JdbcProjectInteractionRepository::mapProjectSummary)
                .list();
    }

    @Override
    public long countInterestedProjects(UUID profileId) {
        return jdbcClient.sql("""
                select count(*)
                from public.project_feedback feedback
                join public.projects p on p.id = feedback.project_id
                where feedback.profile_id = :profileId
                  and feedback.feedback_type = 'INTERESTED'
                  and p.status = 'PUBLISHED'
                """)
                .param("profileId", profileId)
                .query(Long.class)
                .single();
    }

    @Override
    public void setSaved(UUID profileId, UUID projectId, boolean saved) {
        requirePublicProject(projectId);
        List<UUID> changed = saved
                ? jdbcClient.sql("""
                    insert into public.saved_projects (profile_id, project_id)
                    values (:profileId, :projectId)
                    on conflict (profile_id, project_id) do update
                    set saved_at = timezone('utc', now()),
                        unsaved_at = null
                    where saved_projects.unsaved_at is not null
                    returning project_id
                    """)
                        .param("profileId", profileId)
                        .param("projectId", projectId)
                        .query((resultSet, rowNumber) -> resultSet.getObject("project_id", UUID.class))
                        .list()
                : jdbcClient.sql("""
                    update public.saved_projects
                    set unsaved_at = timezone('utc', now())
                    where profile_id = :profileId
                      and project_id = :projectId
                      and unsaved_at is null
                    returning project_id
                    """)
                        .param("profileId", profileId)
                        .param("projectId", projectId)
                        .query((resultSet, rowNumber) -> resultSet.getObject("project_id", UUID.class))
                        .list();

        if (!changed.isEmpty()) {
            insertEvent(profileId, projectId, saved ? "SAVED" : "UNSAVED");
        }
    }

    @Override
    public void setFeedback(UUID profileId, UUID projectId, ProjectFeedbackType feedbackType) {
        requirePublicProject(projectId);
        List<UUID> changed = jdbcClient.sql("""
                insert into public.project_feedback as current_feedback (
                    profile_id, project_id, feedback_type)
                values (:profileId, :projectId, :feedbackType)
                on conflict (profile_id, project_id) do update
                set feedback_type = excluded.feedback_type,
                    changed_at = timezone('utc', now())
                where current_feedback.feedback_type <> excluded.feedback_type
                returning project_id
                """)
                .param("profileId", profileId)
                .param("projectId", projectId)
                .param("feedbackType", feedbackType.name())
                .query((resultSet, rowNumber) -> resultSet.getObject("project_id", UUID.class))
                .list();

        if (!changed.isEmpty()) {
            insertEvent(profileId, projectId, feedbackType.name());
        }
    }

    private void requirePublicProject(UUID projectId) {
        List<UUID> project = jdbcClient.sql("""
                select id
                from public.projects
                where id = :projectId
                  and status = 'PUBLISHED'
                for share
                """)
                .param("projectId", projectId)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .list();
        if (project.isEmpty()) {
            throw new PublicProjectNotFoundException();
        }
    }

    private void insertEvent(UUID profileId, UUID projectId, String eventType) {
        jdbcClient.sql("""
                insert into public.interaction_events (profile_id, project_id, event_type, source)
                values (:profileId, :projectId, :eventType, 'FEED')
                """)
                .param("profileId", profileId)
                .param("projectId", projectId)
                .param("eventType", eventType)
                .update();
    }

    private static DiscoveryFeedItem mapFeedItem(ResultSet resultSet, int rowNumber)
            throws SQLException {
        String feedbackType = resultSet.getString("feedback_type");
        return new DiscoveryFeedItem(
                mapProjectSummary(resultSet, rowNumber),
                resultSet.getBoolean("saved"),
                feedbackType == null || "NONE".equals(feedbackType)
                        ? null
                        : ProjectFeedbackType.valueOf(feedbackType));
    }

    private static ProjectSummary mapProjectSummary(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ProjectSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tagline"),
                resultSet.getString("sector"),
                resultSet.getString("city"),
                resultSet.getString("stage"));
    }
}
