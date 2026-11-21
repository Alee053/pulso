package com.pulso.backend.profile.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.profile.application.ProfileBlockRepository;
import com.pulso.backend.profile.domain.ProfileBlock;

@Repository
public class JdbcProfileBlockRepository implements ProfileBlockRepository {
    private final JdbcClient jdbcClient;

    public JdbcProfileBlockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ProfileBlock> find(UUID blockerProfileId, UUID targetProfileId) {
        return jdbcClient.sql("""
                select blocked_profile_id, created_at
                from public.profile_blocks
                where blocker_profile_id = :blockerProfileId
                  and blocked_profile_id = :targetProfileId
                """)
                .param("blockerProfileId", blockerProfileId)
                .param("targetProfileId", targetProfileId)
                .query((row, rowNumber) -> ProfileBlock.blocked(
                        row.getObject("blocked_profile_id", UUID.class),
                        row.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    @Override
    public ProfileBlock createIfAbsent(UUID blockerProfileId, UUID targetProfileId) {
        jdbcClient.sql("""
                insert into public.profile_blocks (blocker_profile_id, blocked_profile_id)
                values (:blockerProfileId, :targetProfileId)
                on conflict (blocker_profile_id, blocked_profile_id) do nothing
                """)
                .param("blockerProfileId", blockerProfileId)
                .param("targetProfileId", targetProfileId)
                .update();
        return find(blockerProfileId, targetProfileId).orElseThrow();
    }

    @Override
    public void delete(UUID blockerProfileId, UUID targetProfileId) {
        jdbcClient.sql("""
                delete from public.profile_blocks
                where blocker_profile_id = :blockerProfileId
                  and blocked_profile_id = :targetProfileId
                """)
                .param("blockerProfileId", blockerProfileId)
                .param("targetProfileId", targetProfileId)
                .update();
    }
}
