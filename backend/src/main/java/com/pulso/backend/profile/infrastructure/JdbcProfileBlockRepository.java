package com.pulso.backend.profile.infrastructure;

import com.pulso.backend.profile.application.ProfileBlockRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcProfileBlockRepository implements ProfileBlockRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProfileBlockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void block(UUID blockerId, UUID blockedId) {
        // ON CONFLICT DO NOTHING evita error si la relación ya existe
        String sql = """
            INSERT INTO profile_blocks (blocker_id, blocked_id)
            VALUES (?, ?)
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
        """;
        jdbcTemplate.update(sql, blockerId, blockedId);
    }

    @Override
    public void unblock(UUID blockerId, UUID blockedId) {
        String sql = "DELETE FROM profile_blocks WHERE blocker_id = ? AND blocked_id = ?";
        jdbcTemplate.update(sql, blockerId, blockedId);
    }

    @Override
    public boolean isBlockedInAnyDirection(UUID profileA, UUID profileB) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM profile_blocks
                WHERE (blocker_id = ? AND blocked_id = ?)
                   OR (blocker_id = ? AND blocked_id = ?)
            )
        """;
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, profileA, profileB, profileB, profileA);
        return Boolean.TRUE.equals(exists);
    }
}