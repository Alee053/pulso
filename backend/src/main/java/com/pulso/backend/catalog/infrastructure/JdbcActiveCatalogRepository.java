package com.pulso.backend.catalog.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.catalog.application.ActiveCatalogRepository;
import com.pulso.backend.catalog.application.ActiveSectorLookup;
import com.pulso.backend.catalog.domain.SectorCatalogItem;
import com.pulso.backend.catalog.domain.SkillCatalogItem;
import com.pulso.backend.catalog.domain.TagCatalogItem;

@Repository
public class JdbcActiveCatalogRepository implements ActiveCatalogRepository, ActiveSectorLookup {

    private final JdbcClient jdbcClient;

    public JdbcActiveCatalogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<SectorCatalogItem> findActiveSectors(int limit) {
        return jdbcClient.sql("""
                select id, slug, name, description
                from public.sectors
                where is_active = true
                order by sort_order asc, name asc, id asc
                limit :limit
                """)
                .param("limit", limit)
                .query(JdbcActiveCatalogRepository::mapSector)
                .list();
    }

    @Override
    public List<TagCatalogItem> findActiveTags(int limit) {
        return jdbcClient.sql("""
                select id, slug, name, tag_type
                from public.tags
                where is_active = true
                order by name asc, id asc
                limit :limit
                """)
                .param("limit", limit)
                .query(JdbcActiveCatalogRepository::mapTag)
                .list();
    }

    @Override
    public List<SkillCatalogItem> findActiveSkills(int limit) {
        return jdbcClient.sql("""
                select id, slug, name, category
                from public.skills
                where is_active = true
                order by name asc, id asc
                limit :limit
                """)
                .param("limit", limit)
                .query(JdbcActiveCatalogRepository::mapSkill)
                .list();
    }

    @Override
    public Optional<UUID> findActiveSectorId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        try {
            return findActiveSectorIdById(UUID.fromString(identifier));
        } catch (IllegalArgumentException exception) {
            return findActiveSectorIdBySlug(identifier);
        }
    }

    private Optional<UUID> findActiveSectorIdById(UUID sectorId) {
        return jdbcClient.sql("""
                select id
                from public.sectors
                where id = :sectorId
                  and is_active = true
                """)
                .param("sectorId", sectorId)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .list()
                .stream()
                .findFirst();
    }

    private Optional<UUID> findActiveSectorIdBySlug(String slug) {
        return jdbcClient.sql("""
                select id
                from public.sectors
                where slug = :slug
                  and is_active = true
                """)
                .param("slug", slug)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .list()
                .stream()
                .findFirst();
    }

    private static SectorCatalogItem mapSector(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SectorCatalogItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("description"));
    }

    private static TagCatalogItem mapTag(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TagCatalogItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tag_type"));
    }

    private static SkillCatalogItem mapSkill(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SkillCatalogItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("category"));
    }
}
