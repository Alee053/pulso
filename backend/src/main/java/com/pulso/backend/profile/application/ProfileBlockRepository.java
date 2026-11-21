package com.pulso.backend.profile.application;

import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.profile.domain.ProfileBlock;

public interface ProfileBlockRepository {
    Optional<ProfileBlock> find(UUID blockerProfileId, UUID targetProfileId);

    ProfileBlock createIfAbsent(UUID blockerProfileId, UUID targetProfileId);

    void delete(UUID blockerProfileId, UUID targetProfileId);
}
