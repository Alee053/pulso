package com.pulso.backend.profile.application;

import java.util.UUID;

public interface ProfileBlockRepository {
    void block(UUID blockerId, UUID blockedId);
    void unblock(UUID blockerId, UUID blockedId);
    boolean isBlockedInAnyDirection(UUID profileA, UUID profileB);
}