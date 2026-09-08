package com.pulso.backend.profile.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.pulso.backend.profile.domain.Profile;

public record ProfileResponse(
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

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.id(),
                profile.profileKind(),
                profile.displayName(),
                profile.avatarStoragePath(),
                profile.shortBio(),
                profile.city(),
                profile.department(),
                profile.profileVisibility(),
                profile.verificationStatus(),
                profile.accountStatus(),
                profile.platformRole(),
                profile.onboardingCompletedAt(),
                profile.createdAt(),
                profile.updatedAt());
    }
}
