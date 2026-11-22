package com.pulso.backend.profile.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.profile.application.ProfileBlockRepository;
import com.pulso.backend.profile.domain.BlockedProfile;

@Repository
public class JdbcProfileBlockRepository implements ProfileBlockRepository {

    private final JdbcClient jdbc;

    public JdbcProfileBlockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean blockActiveProfiles(UUID blockerId, UUID blockedId) {
        List<UUID> activeProfiles = jdbc.sql("""
                select id
                from public.profiles
                where id in (:blockerId, :blockedId)
                  and account_status = 'ACTIVE'
                  and deleted_at is null
                order by id
                for share
                """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .query(UUID.class)
                .list();
        if (activeProfiles.size() != 2) {
            return false;
        }

        lockContactPair(blockerId, blockedId);
        jdbc.sql("""
                insert into public.profile_blocks (blocker_profile_id, blocked_profile_id)
                values (:blockerId, :blockedId)
                on conflict (blocker_profile_id, blocked_profile_id) do nothing
                """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .update();
        return true;
    }

    @Override
    public void unblock(UUID blockerId, UUID blockedId) {
        lockContactPair(blockerId, blockedId);
        jdbc.sql("""
                delete from public.profile_blocks
                where blocker_profile_id = :blockerId and blocked_profile_id = :blockedId
                """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .update();
    }

    @Override
    public List<BlockedProfile> findPage(UUID blockerId, int offset, int limit) {
        return jdbc.sql("""
                select block.blocked_profile_id,
                       case when profile.profile_visibility = 'PUBLIC'
                                  and profile.account_status = 'ACTIVE'
                                  and profile.deleted_at is null
                            then profile.display_name
                       end as display_name
                from public.profile_blocks block
                join public.profiles profile on profile.id = block.blocked_profile_id
                where block.blocker_profile_id = :blockerId
                order by block.blocked_profile_id
                offset :offset
                limit :limit
                """)
                .param("blockerId", blockerId)
                .param("offset", offset)
                .param("limit", limit)
                .query((row, rowNumber) -> new BlockedProfile(
                        row.getObject("blocked_profile_id", UUID.class),
                        row.getString("display_name")))
                .list();
    }

    private void lockContactPair(UUID blockerId, UUID blockedId) {
        // Take the pair lock before either write can acquire a row lock on profile_blocks.
        jdbc.sql("""
                select true from (
                    select pg_advisory_xact_lock(public.profile_contact_lock_key(:blockerId, :blockedId))
                ) contact_lock
                """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .query(Boolean.class)
                .single();
    }
}
