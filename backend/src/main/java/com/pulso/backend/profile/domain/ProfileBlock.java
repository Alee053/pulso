package com.pulso.backend.profile.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileBlock(UUID targetProfileId, boolean blocked, OffsetDateTime createdAt) {
    public static ProfileBlock blocked(UUID targetProfileId, OffsetDateTime createdAt) {
        return new ProfileBlock(targetProfileId, true, createdAt);
    }

    public static ProfileBlock unblocked(UUID targetProfileId) {
        return new ProfileBlock(targetProfileId, false, null);
    }
}
