package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.application.ProjectSearchRepository;
import com.pulso.backend.project.domain.ProjectSearchFilters;

@Repository
public class JdbcProjectSearchRepository implements ProjectSearchRepository {

    private static final String PROJECT_COLUMNS = """
            p.id,
            p.slug,
            p.name,
            p.tagline,
            s.name as sector,
            coalesce(p.city, '') as city,
            p.stage,
            jsonb_path_query_first(p.media, '$[*] ? (@.type == "COVER")') ->> 'url' as image_url,
            jsonb_path_query_first(p.media, '$[*] ? (@.type == "COVER")') ->> 'alt' as image_alt
            """;

    private final JdbcClient jdbcClient;

    public JdbcProjectSearchRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<ProjectSummary> search(ProjectSearchFilters filters, int offset, int limit) {
        SearchQuery query = buildQuery(filters, true);
        return jdbcClient.sql(query.sql() + " offset :offset limit :limit")
                .params(query.params())
                .param("offset", offset)
                .param("limit", limit)
                .query(JdbcProjectSearchRepository::mapProjectSummary)
                .list();
    }

    @Override
    public long count(ProjectSearchFilters filters) {
        SearchQuery query = buildQuery(filters, false);
        return jdbcClient.sql(query.sql())
                .params(query.params())
                .query(Long.class)
                .single();
    }

    private SearchQuery buildQuery(ProjectSearchFilters filters, boolean isSelect) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new java.util.HashMap<>();
        List<String> conditions = new ArrayList<>();

        // 1. Filtro obligatorio de publicación
        conditions.add("p.status = 'PUBLISHED'");

        // 2. Búsqueda por texto
         if (filters.query() != null && !filters.query().isBlank()) {
            conditions.add("(p.name ilike :query or p.tagline ilike :query or p.description ilike :query or p.city ilike :query or p.department ilike :query)");
            params.put("query", "%" + filters.query().trim() + "%");
        }

        // 3. Filtro por sector
        if (filters.sector() != null && !filters.sector().isBlank()) {
            conditions.add("(s.slug = :sector or s.id::text = :sector)");
            params.put("sector", filters.sector());
        }

        // 4. Filtro por etapa
        if (filters.stage() != null && !filters.stage().isBlank()) {
            conditions.add("p.stage = :stage");
            params.put("stage", filters.stage());
        }

        // 5. Filtro por ubicación
        if (filters.location() != null && !filters.location().isBlank()) {
            conditions.add("(p.city ilike :location or p.department ilike :location)");
            params.put("location", "%" + filters.location().trim() + "%");
        }

        // 6. Filtro por tags
        if (filters.tags() != null && !filters.tags().isEmpty()) {
            conditions.add("""
                    exists (
                        select 1 from public.project_tags pt
                        join public.tags t on t.id = pt.tag_id
                        where pt.project_id = p.id and t.slug in (:tags)
                    )
                    """);
            params.put("tags", filters.tags());
        }

        // 7. Filtro por oportunidades abiertas
        if (Boolean.TRUE.equals(filters.hasOpenOpportunities())) {
            conditions.add("""
                    exists (
                        select 1 from public.project_opportunities po
                        where po.project_id = p.id and po.status = 'OPEN'
                    )
                    """);
        }

        String whereClause = "where " + String.join(" and ", conditions);
        String baseFrom = """
                from public.projects p
                join public.sectors s on s.id = p.sector_id
                """ + whereClause;

        if (isSelect) {
            sql.append("select ").append(PROJECT_COLUMNS).append(" ").append(baseFrom);
            sql.append(" order by p.published_at desc, p.id desc");
        } else {
            sql.append("select count(*) ").append(baseFrom);
        }

        return new SearchQuery(sql.toString(), params);
    }

    private record SearchQuery(String sql, Map<String, Object> params) {}

    private static ProjectSummary mapProjectSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tagline"),
                resultSet.getString("sector"),
                resultSet.getString("city"),
                resultSet.getString("stage"),
                resultSet.getString("image_url"),
                resultSet.getString("image_alt"));
    }
}