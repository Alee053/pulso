package com.pulso.backend.profile.domain;

import java.time.OffsetDateTime;
import java.util.Locale;

import com.pulso.backend.config.ApiMessages;

public record ProfilePatch(
        String profileKind,
        String displayName,
        String avatarStoragePath,
        String shortBio,
        String city,
        String department,
        String profileVisibility,
        OffsetDateTime onboardingCompletedAt,
        Limits limits) {

    public record Limits(
            int displayNameMaxLength,
            int avatarPathMaxLength,
            int shortBioMaxLength,
            int cityMaxLength,
            int departmentMaxLength) {
    }

    public ProfilePatch {
        profileKind = optionalEnum(profileKind, "profileKind", "PERSON", "ORGANIZATION");
        displayName = optionalText(displayName, "displayName", limits.displayNameMaxLength());
        avatarStoragePath = optionalText(
                avatarStoragePath,
                "avatarStoragePath",
                limits.avatarPathMaxLength());
        shortBio = optionalText(shortBio, "shortBio", limits.shortBioMaxLength());
        city = optionalText(city, "city", limits.cityMaxLength());
        department = optionalText(department, "department", limits.departmentMaxLength());
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
            throw new ProfileValidationException(
                    String.format(ApiMessages.PROFILE_BLANK, field));
        }
        if (normalized.length() > maxLength) {
            throw new ProfileValidationException(
                    String.format(ApiMessages.PROFILE_MAX_LENGTH, field, maxLength));
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
                String.format(ApiMessages.PROFILE_ENUM, field, String.join(", ", allowedValues)));
    }
}
