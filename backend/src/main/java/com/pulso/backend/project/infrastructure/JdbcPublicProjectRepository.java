package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.PublicProjectRepository;
import com.pulso.backend.project.domain.PublicProjectDetail;

@Repository
public class JdbcPublicProjectRepository implements PublicProjectRepository {

    private static final String PUBLIC_PROJECT_COLUMNS = """
            select
                p.id,
                p.slug,
                p.name,
                p.tagline,
                p.description,
                p.value_proposition,
                p.stage,
                p.city,
                p.department,
                s.id as sector_id,
                s.slug as sector_slug,
                s.name as sector_name,
                coalesce((
                    select jsonb_agg(
                        jsonb_build_object(
                            'id', t.id,
                            'slug', t.slug,
                            'name', t.name
                        )
                        order by t.name asc, t.id asc
                    )
                    from public.project_tags pt
                    join public.tags t on t.id = pt.tag_id
                    where pt.project_id = p.id
                      and t.is_active = true
                ), '[]'::jsonb)::text as tags,
                (
                    case
                        when jsonb_typeof(coalesce(p.external_links, '[]'::jsonb)) = 'array'
                            then coalesce(p.external_links, '[]'::jsonb)
                        else '[]'::jsonb
                    end
                )::text as external_links,
                coalesce((
                    select jsonb_agg(
                        jsonb_build_object(
                            'profileId', profile.id,
                            'displayName', profile.display_name,
                            'avatarStoragePath', profile.avatar_storage_path,
                            'displayTitle', member.display_title,
                            'isFeatured', member.is_featured
                        )
                        order by member.is_featured desc, profile.display_name asc, profile.id asc
                    )
                    from public.project_members member
                    join public.profiles profile on profile.id = member.profile_id
                    where member.project_id = p.id
                      and member.membership_status = 'ACTIVE'
                      and member.is_public = true
                      and profile.account_status = 'ACTIVE'
                ), '[]'::jsonb)::text as members
            from public.projects p
            join public.sectors s on s.id = p.sector_id
                and s.is_active = true
            where p.status = 'PUBLISHED'
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcPublicProjectRepository(JdbcClient jdbcClient) {
        this(jdbcClient, new ObjectMapper());
    }

    @Autowired
    public JdbcPublicProjectRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PublicProjectDetail> findPublishedByReference(String projectRef) {
        try {
            return findPublishedById(UUID.fromString(projectRef));
        } catch (IllegalArgumentException exception) {
            return findPublishedBySlug(projectRef);
        }
    }

    private Optional<PublicProjectDetail> findPublishedById(UUID projectId) {
        return jdbcClient.sql(PUBLIC_PROJECT_COLUMNS + "and p.id = :projectId")
                .param("projectId", projectId)
                .query(this::mapProject)
                .list()
                .stream()
                .findFirst();
    }

    private Optional<PublicProjectDetail> findPublishedBySlug(String slug) {
        return jdbcClient.sql(PUBLIC_PROJECT_COLUMNS + "and p.slug = :slug")
                .param("slug", slug)
                .query(this::mapProject)
                .list()
                .stream()
                .findFirst();
    }

    private PublicProjectDetail mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublicProjectDetail(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tagline"),
                resultSet.getString("description"),
                resultSet.getString("value_proposition"),
                new PublicProjectDetail.Sector(
                        resultSet.getObject("sector_id", UUID.class),
                        resultSet.getString("sector_slug"),
                        resultSet.getString("sector_name")),
                resultSet.getString("stage"),
                resultSet.getString("city"),
                resultSet.getString("department"),
                parseTags(resultSet.getString("tags")),
                parseExternalLinks(resultSet.getString("external_links")),
                parseMembers(resultSet.getString("members")));
    }

    private List<PublicProjectDetail.Tag> parseTags(String json) {
        List<PublicProjectDetail.Tag> tags = new ArrayList<>();
        forEachObject(json, node -> tags.add(new PublicProjectDetail.Tag(
                uuid(node, "id"),
                text(node, "slug"),
                text(node, "name"))));
        return tags;
    }

    private List<PublicProjectDetail.ExternalLink> parseExternalLinks(String json) {
        List<PublicProjectDetail.ExternalLink> links = new ArrayList<>();
        forEachObject(json, node -> links.add(new PublicProjectDetail.ExternalLink(
                text(node, "type"),
                text(node, "url"),
                text(node, "label"))));
        return links;
    }

    private List<PublicProjectDetail.Member> parseMembers(String json) {
        List<PublicProjectDetail.Member> members = new ArrayList<>();
        forEachObject(json, node -> members.add(new PublicProjectDetail.Member(
                uuid(node, "profileId"),
                text(node, "displayName"),
                text(node, "avatarStoragePath"),
                text(node, "displayTitle"),
                node.path("isFeatured").asBoolean(false))));
        return members;
    }

    private void forEachObject(String json, java.util.function.Consumer<JsonNode> consumer) {
        if (json == null || json.isBlank()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return;
            }
            root.elements().forEachRemaining(node -> {
                if (node.isObject()) {
                    consumer.accept(node);
                }
            });
        } catch (JsonProcessingException exception) {
            // PostgreSQL guarantees valid JSONB. Treat an invalid public shape
            // as an empty relation rather than leaking persistence details.
        }
    }

    private static UUID uuid(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        return value == null ? null : UUID.fromString(value);
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }
}
