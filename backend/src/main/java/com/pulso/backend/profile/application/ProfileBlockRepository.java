package com.pulso.backend.profile.application;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.profile.domain.BlockedProfile;

public interface ProfileBlockRepository {
    boolean blockActiveProfiles(UUID blockerId, UUID blockedId);

    void unblock(UUID blockerId, UUID blockedId);

    List<BlockedProfile> findPage(UUID blockerId, int offset, int limit);
}
