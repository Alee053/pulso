package com.pulso.backend.matching.infrastructure;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.matching.application.OpportunityMatchRepository;
import com.pulso.backend.matching.domain.MatchOpportunity;
import com.pulso.backend.matching.domain.MatchProfile;

@Repository
public class JdbcOpportunityMatchRepository implements OpportunityMatchRepository {

    /** Same cap and order as the open opportunities of the public project detail. */
    static final int MAX_OPPORTUNITIES = 50;

    private static final String PUBLISHED_PROJECT = """
            select p.id, p.stage, s.id as sector_id, s.slug as sector_slug, s.name as sector_name
            from public.projects p
            join public.sectors s on s.id = p.sector_id and s.is_active = true
            where p.status = 'PUBLISHED'
            """;

    private final JdbcClient jdbcClient;

    public JdbcOpportunityMatchRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<List<MatchOpportunity>> findOpenOpportunities(String projectRef) {
        Optional<ProjectRow> project = findPublishedProject(projectRef);
        if (project.isEmpty()) {
            return Optional.empty();
        }

        ProjectRow row = project.get();
        List<MatchOpportunity.Tag> tags = jdbcClient.sql("""
                select t.id, t.name
                from public.project_tags pt
                join public.tags t on t.id = pt.tag_id and t.is_active = true
                where pt.project_id = :projectId
                order by t.name, t.id
                """)
                .param("projectId", row.id())
                .query((resultSet, rowNumber) -> new MatchOpportunity.Tag(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name")))
                .list();
        List<OpportunityRow> opportunities = jdbcClient.sql("""
                select id, title, opportunity_type, city, department, remote_allowed
                from public.project_opportunities
                where project_id = :projectId and status = 'OPEN'
                order by created_at desc, id desc
                limit :limit
                """)
                .param("projectId", row.id())
                .param("limit", MAX_OPPORTUNITIES)
                .query((resultSet, rowNumber) -> new OpportunityRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("title"),
                        resultSet.getString("opportunity_type"),
                        resultSet.getString("city"),
                        resultSet.getString("department"),
                        resultSet.getBoolean("remote_allowed")))
                .list();

        Map<UUID, List<MatchOpportunity.Skill>> skills = skillsByOpportunity(
                opportunities.stream().map(OpportunityRow::id).toList());
        MatchOpportunity.Sector sector = new MatchOpportunity.Sector(
                row.sectorId(), row.sectorSlug(), row.sectorName());

        return Optional.of(opportunities.stream()
                .map(opportunity -> new MatchOpportunity(
                        opportunity.id(),
                        opportunity.title(),
                        opportunity.type(),
                        opportunity.city(),
                        opportunity.department(),
                        opportunity.remoteAllowed(),
                        skills.getOrDefault(opportunity.id(), List.of()),
                        sector,
                        row.stage(),
                        tags))
                .toList());
    }

    @Override
    public Optional<MatchProfile> findProfile(UUID profileId) {
        return jdbcClient.sql("""
                select
                    profile.onboarding_completed_at is not null as onboarding_completed,
                    profile.city,
                    profile.department,
                    array(select jsonb_array_elements_text(coalesce(preferences.intentions, '[]'::jsonb)))
                        as intentions,
                    array(select jsonb_array_elements_text(coalesce(preferences.preferred_sectors, '[]'::jsonb)))
                        as preferred_sectors,
                    array(select jsonb_array_elements_text(coalesce(preferences.preferred_stages, '[]'::jsonb)))
                        as preferred_stages,
                    array(select tag_id::text from public.profile_tags where profile_id = profile.id)
                        as tag_ids,
                    array(select skill_id::text from public.profile_skills where profile_id = profile.id)
                        as skill_ids
                from public.profiles profile
                left join public.profile_preferences preferences on preferences.profile_id = profile.id
                where profile.id = :profileId and profile.deleted_at is null
                """)
                .param("profileId", profileId)
                .query((resultSet, rowNumber) -> new MatchProfile(
                        resultSet.getBoolean("onboarding_completed"),
                        strings(resultSet, "intentions"),
                        strings(resultSet, "preferred_sectors"),
                        strings(resultSet, "preferred_stages"),
                        uuids(resultSet, "tag_ids"),
                        uuids(resultSet, "skill_ids"),
                        resultSet.getString("city"),
                        resultSet.getString("department")))
                .optional();
    }

    private Optional<ProjectRow> findPublishedProject(String projectRef) {
        try {
            return jdbcClient.sql(PUBLISHED_PROJECT + "and p.id = :projectId")
                    .param("projectId", UUID.fromString(projectRef))
                    .query(JdbcOpportunityMatchRepository::mapProject)
                    .optional();
        } catch (IllegalArgumentException exception) {
            return jdbcClient.sql(PUBLISHED_PROJECT + "and p.slug = :slug")
                    .param("slug", projectRef)
                    .query(JdbcOpportunityMatchRepository::mapProject)
                    .optional();
        }
    }

    private Map<UUID, List<MatchOpportunity.Skill>> skillsByOpportunity(List<UUID> opportunityIds) {
        if (opportunityIds.isEmpty()) {
            return Map.of();
        }

        List<SkillRow> rows = jdbcClient.sql("""
                select association.opportunity_id, skill.id, skill.name, association.importance
                from public.opportunity_skills association
                join public.skills skill on skill.id = association.skill_id and skill.is_active = true
                where association.opportunity_id in (:opportunityIds)
                order by skill.name, skill.id
                """)
                .param("opportunityIds", opportunityIds)
                .query((resultSet, rowNumber) -> new SkillRow(
                        resultSet.getObject("opportunity_id", UUID.class),
                        new MatchOpportunity.Skill(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("name"),
                                "REQUIRED".equals(resultSet.getString("importance")))))
                .list();
        return rows.stream().collect(Collectors.groupingBy(
                SkillRow::opportunityId,
                Collectors.mapping(SkillRow::skill, Collectors.toList())));
    }

    private static ProjectRow mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("stage"),
                resultSet.getObject("sector_id", UUID.class),
                resultSet.getString("sector_slug"),
                resultSet.getString("sector_name"));
    }

    private static Set<String> strings(ResultSet resultSet, String column) throws SQLException {
        Array array = resultSet.getArray(column);
        Set<String> values = new HashSet<>();
        if (array != null) {
            for (Object value : (Object[]) array.getArray()) {
                values.add((String) value);
            }
        }
        return values;
    }

    private static Set<UUID> uuids(ResultSet resultSet, String column) throws SQLException {
        Set<UUID> values = new HashSet<>();
        for (String value : strings(resultSet, column)) {
            values.add(UUID.fromString(value));
        }
        return values;
    }

    private record ProjectRow(UUID id, String stage, UUID sectorId, String sectorSlug, String sectorName) {
    }

    private record SkillRow(UUID opportunityId, MatchOpportunity.Skill skill) {
    }

    private record OpportunityRow(
            UUID id,
            String title,
            String type,
            String city,
            String department,
            boolean remoteAllowed) {
    }
}
