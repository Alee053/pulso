package com.pulso.backend.profile.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Profile(
        UUID id,
        String profileKind,
        String displayName,
        String avatarStoragePath,
        String shortBio,
        String city,
        String department,
        String profileVisibility,
        String verificationStatus,
        String accountStatus,
        String platformRole,
        OffsetDateTime onboardingCompletedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
