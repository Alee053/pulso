package com.pulso.backend.interaction.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.interaction.application.ProjectInteractionRepository;
import com.pulso.backend.interaction.domain.DiscoveryFeedCursor;
import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.application.PublicProjectNotFoundException;
import com.pulso.backend.project.domain.ProjectShareOrigin;

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
    public List<DiscoveryFeedItem> findDiscoveryFeed(
            UUID profileId,
            List<String> preferredSectors,
            List<String> preferredStages,
            int offset,
            DiscoveryFeedCursor cursor,
            int limit) {
        String sectorMatch = preferredSectors.isEmpty()
                ? "false"
                : "(s.id::text in (:preferredSectors) or s.slug in (:preferredSectors))";
        String stageMatch = preferredStages.isEmpty() ? "false" : "p.stage in (:preferredStages)";
        String cursorCondition = cursor == null
                ? ""
                : "where (r.preference_score, r.published_at, r.id) "
                        + "< (:cursorScore, :cursorPublishedAt, :cursorProjectId)";
        String offsetClause = cursor == null ? "offset :offset" : "";
        var statement = jdbcClient.sql("""
                with ranked_projects as (
                    select %s,
                           (case when %s then 1 else 0 end
                             + case when %s then 1 else 0 end) as preference_score,
                           coalesce(p.published_at, p.created_at) as published_at
                    from public.projects p
                    join public.sectors s on s.id = p.sector_id
                    where p.status = 'PUBLISHED'
                      and not exists (
                          select 1
                          from public.project_feedback excluded
                          where excluded.profile_id = :profileId
                            and excluded.project_id = p.id
                            and excluded.feedback_type in ('INTERESTED', 'DISMISSED')
                      )
                )
                select r.id, r.slug, r.name, r.tagline, r.sector, r.city, r.stage,
                       r.preference_score, r.published_at,
                       (sp.project_id is not null) as saved,
                       pf.feedback_type
                from ranked_projects r
                left join public.saved_projects sp
                  on sp.project_id = r.id
                 and sp.profile_id = :profileId
                 and sp.unsaved_at is null
                left join public.project_feedback pf
                  on pf.project_id = r.id
                 and pf.profile_id = :profileId
                %s
                order by r.preference_score desc, r.published_at desc, r.id desc
                %s
                limit :limit
                """.formatted(PROJECT_COLUMNS, sectorMatch, stageMatch, cursorCondition, offsetClause))
                .param("profileId", profileId)
                .param("limit", limit);
        if (!preferredSectors.isEmpty()) {
            statement.param("preferredSectors", preferredSectors);
        }
        if (!preferredStages.isEmpty()) {
            statement.param("preferredStages", preferredStages);
        }
        if (cursor == null) {
            statement.param("offset", offset);
        } else {
            statement.param("cursorScore", cursor.preferenceScore())
                    .param("cursorPublishedAt", cursor.publishedAt())
                    .param("cursorProjectId", cursor.projectId());
        }
        return statement.query(JdbcProjectInteractionRepository::mapFeedItem).list();
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
    public ProjectFeedbackType findFeedback(UUID profileId, UUID projectId) {
        requirePublicProject(projectId);
        List<String> states = jdbcClient.sql("""
                select feedback_type
                from public.project_feedback
                where profile_id = :profileId
                  and project_id = :projectId
                """)
                .param("profileId", profileId)
                .param("projectId", projectId)
                .query((resultSet, rowNumber) -> resultSet.getString("feedback_type"))
                .list();
        if (states.isEmpty() || "NONE".equals(states.getFirst())) {
            return null;
        }
        return ProjectFeedbackType.valueOf(states.getFirst());
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

    @Override
    public void clearFeedback(UUID profileId, UUID projectId) {
        requirePublicProject(projectId);
        List<UUID> changed = jdbcClient.sql("""
                update public.project_feedback
                set feedback_type = 'NONE',
                    changed_at = timezone('utc', now())
                where profile_id = :profileId
                  and project_id = :projectId
                  and feedback_type <> 'NONE'
                returning project_id
                """)
                .param("profileId", profileId)
                .param("projectId", projectId)
                .query((resultSet, rowNumber) -> resultSet.getObject("project_id", UUID.class))
                .list();

        if (!changed.isEmpty()) {
            insertEvent(profileId, projectId, "FEEDBACK_CLEARED");
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
                        : ProjectFeedbackType.valueOf(feedbackType),
                resultSet.getInt("preference_score"),
                resultSet.getObject("published_at", java.time.OffsetDateTime.class));
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

    @Override
public void insertSharedEvent(UUID profileId, UUID projectId, ProjectShareOrigin origin) {
    requirePublicProject(projectId);
    jdbcClient.sql("""
            insert into public.interaction_events (profile_id, project_id, event_type, source)
            values (:profileId, :projectId, 'SHARED', :source)
            """)
            .param("profileId", profileId)
            .param("projectId", projectId)
            .param("source", origin.name())
            .update();
}
}
