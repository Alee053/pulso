package com.pulso.backend.profile.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.profile.domain.ProfileBlock;
import com.pulso.backend.profile.domain.ProfileValidationException;

@Service
public class ManageProfileBlocks {
    private final ProfileRepository profiles;
    private final ProfileBlockRepository blocks;

    public ManageProfileBlocks(ProfileRepository profiles, ProfileBlockRepository blocks) {
        this.profiles = profiles;
        this.blocks = blocks;
    }

    @Transactional(readOnly = true)
    public ProfileBlock status(UUID actorProfileId, UUID targetProfileId) {
        requireDifferentProfiles(actorProfileId, targetProfileId);
        requireActiveTarget(targetProfileId);
        return blocks.find(actorProfileId, targetProfileId)
                .orElseGet(() -> ProfileBlock.unblocked(targetProfileId));
    }

    @Transactional
    public ProfileBlock block(UUID actorProfileId, UUID targetProfileId) {
        requireDifferentProfiles(actorProfileId, targetProfileId);
        requireActiveTarget(targetProfileId);
        return blocks.createIfAbsent(actorProfileId, targetProfileId);
    }

    @Transactional
    public ProfileBlock unblock(UUID actorProfileId, UUID targetProfileId) {
        requireDifferentProfiles(actorProfileId, targetProfileId);
        requireActiveTarget(targetProfileId);
        blocks.delete(actorProfileId, targetProfileId);
        return ProfileBlock.unblocked(targetProfileId);
    }

    private void requireActiveTarget(UUID targetProfileId) {
        profiles.findById(targetProfileId)
                .filter(value -> "ACTIVE".equals(value.accountStatus()))
                .orElseThrow(ProfileNotFoundException::new);
    }

    private static void requireDifferentProfiles(UUID actorProfileId, UUID targetProfileId) {
        if (actorProfileId == null || targetProfileId == null) {
            throw new ProfileValidationException("Profile identifiers are required.");
        }
        if (actorProfileId.equals(targetProfileId)) {
            throw new ProfileValidationException("A profile cannot block itself.");
        }
    }
}
