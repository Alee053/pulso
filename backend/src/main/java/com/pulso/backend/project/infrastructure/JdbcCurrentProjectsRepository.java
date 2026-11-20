package com.pulso.backend.project.infrastructure;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.CurrentProjectsRepository;
import com.pulso.backend.project.domain.CurrentProject;

@Repository
public class JdbcCurrentProjectsRepository implements CurrentProjectsRepository {
    private final JdbcClient jdbcClient;

    public JdbcCurrentProjectsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<CurrentProject> findActive(UUID profileId, int offset, int limit) {
        return jdbcClient.sql("""
                select p.id, p.name, m.role, m.membership_status,
                       p.status, p.verification_status, p.review_note, p.reviewed_at
                from public.project_members m
                join public.projects p on p.id = m.project_id
                where m.profile_id = :profileId and m.membership_status = 'ACTIVE'
                order by p.name, p.id
                limit :limit offset :offset
                """)
                .param("profileId", profileId)
                .param("limit", limit)
                .param("offset", offset)
                .query((row, rowNumber) -> new CurrentProject(
                        row.getObject("id", UUID.class), row.getString("name"),
                        row.getString("role"), row.getString("membership_status"),
                        row.getString("status"), row.getString("verification_status"), row.getString("review_note"),
                        row.getObject("reviewed_at", OffsetDateTime.class) == null ? null
                                : row.getObject("reviewed_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
