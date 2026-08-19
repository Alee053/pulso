package com.pulso.backend.discovery.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.discovery.application.StartupCatalog;
import com.pulso.backend.discovery.domain.StartupSummary;

@Repository
public class JdbcStartupCatalog implements StartupCatalog {

    private final JdbcClient jdbcClient;

    public JdbcStartupCatalog(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<StartupSummary> findPublished(int offset, int limit) {
        return jdbcClient.sql("""
                select id, slug, name, tagline, sector, city, stage
                from public.startups
                where status = 'PUBLISHED'
                order by created_at desc, id desc
                offset :offset
                limit :limit
                """)
                .param("offset", offset)
                .param("limit", limit)
                .query(JdbcStartupCatalog::mapStartup)
                .list();
    }

    private static StartupSummary mapStartup(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StartupSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tagline"),
                resultSet.getString("sector"),
                resultSet.getString("city"),
                resultSet.getString("stage"));
    }
}
