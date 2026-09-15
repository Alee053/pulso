package com.pulso.backend.profile.domain;

import java.time.OffsetDateTime;
import java.util.Locale;

public record ProfilePatch(
        String profileKind,
        String displayName,
        String avatarStoragePath,
        String shortBio,
        String city,
        String department,
        String profileVisibility,
        OffsetDateTime onboardingCompletedAt) {

    public ProfilePatch {
        profileKind = optionalEnum(
                profileKind,
                "profileKind",
                "PERSON",
                "ORGANIZATION");
        displayName = optionalText(displayName, "displayName", 120);
        avatarStoragePath = optionalText(avatarStoragePath, "avatarStoragePath", 500);
        shortBio = optionalText(shortBio, "shortBio", 500);
        city = optionalText(city, "city", 120);
        department = optionalText(department, "department", 120);
        profileVisibility = optionalEnum(
                profileVisibility,
                "profileVisibility",
                "PUBLIC",
                "MEMBERS_ONLY",
                "PRIVATE");
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ProfileValidationException(field + " must not be blank.");
        }
        if (normalized.length() > maxLength) {
            throw new ProfileValidationException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private static String optionalEnum(String value, String field, String... allowedValues) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(normalized)) {
                return normalized;
            }
        }

        throw new ProfileValidationException(
                field + " must be one of " + String.join(", ", allowedValues) + ".");
    }
}
