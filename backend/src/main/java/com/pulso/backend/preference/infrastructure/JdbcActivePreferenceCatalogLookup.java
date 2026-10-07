package com.pulso.backend.preference.infrastructure;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.preference.application.ActivePreferenceCatalogLookup;

@Repository
public class JdbcActivePreferenceCatalogLookup implements ActivePreferenceCatalogLookup {

    private final JdbcClient jdbcClient;

    public JdbcActivePreferenceCatalogLookup(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UUID> findActiveTagIds(List<UUID> ids) {
        return findActiveIds("public.tags", ids);
    }

    @Override
    public Set<UUID> findActiveSkillIds(List<UUID> ids) {
        return findActiveIds("public.skills", ids);
    }

    private Set<UUID> findActiveIds(String table, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }

        return Set.copyOf(jdbcClient.sql("""
                select id
                from %s
                where is_active = true
                  and id in (:ids)
                """.formatted(table))
                .param("ids", ids)
                .query((resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .list());
    }
}
