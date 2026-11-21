package com.pulso.backend.profile.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.profile.domain.BlockedProfile;
import com.pulso.backend.profile.domain.ProfileValidationException;

@Service
public class ManageProfileBlocks {

    private final ProfileBlockRepository repository;

    public ManageProfileBlocks(ProfileBlockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void block(UUID blockerId, UUID targetId) {
        if (blockerId.equals(targetId)) {
            throw new ProfileValidationException(ApiMessages.PROFILE_BLOCK_SELF);
        }
        if (!repository.blockActiveProfiles(blockerId, targetId)) {
            throw new ProfileNotFoundException(ApiMessages.PROFILE_BLOCK_TARGET_UNAVAILABLE);
        }
    }

    @Transactional
    public void unblock(UUID blockerId, UUID targetId) {
        repository.unblock(blockerId, targetId);
    }

    @Transactional(readOnly = true)
    public Page list(UUID blockerId, int offset, int limit) {
        List<BlockedProfile> window = repository.findPage(blockerId, offset, limit + 1);
        boolean hasMore = window.size() > limit;
        return new Page(hasMore ? window.subList(0, limit) : window, hasMore);
    }

    public record Page(List<BlockedProfile> profiles, boolean hasMore) {
    }
}
