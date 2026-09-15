package com.pulso.backend.project.domain;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public record ProjectDraft(
        String slug,
        String name,
        String tagline,
        String description,
        UUID sectorId,
        String city,
        String stage) {

    private static final Set<String> ALLOWED_STAGES = Set.of(
            "IDEA",
            "PROTOTYPE",
            "MVP",
            "EARLY_STAGE",
            "GROWING",
            "ESTABLISHED");

    public ProjectDraft {
        slug = requiredText(slug, "slug", 120).toLowerCase(Locale.ROOT);
        if (slug.length() < 2) {
            throw new ProjectValidationException("slug must be at least 2 characters.");
        }
        name = requiredText(name, "name", 160);
        tagline = requiredText(tagline, "tagline", 240);
        description = optionalText(description, "description", 5_000);
        if (sectorId == null) {
            throw new ProjectValidationException("sectorId is required.");
        }
        city = requiredText(city, "city", 120);
        stage = requiredEnum(stage, "stage", ALLOWED_STAGES);
    }

    private static String requiredText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) {
            throw new ProjectValidationException(field + " is required.");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ProjectValidationException(field + " must not be blank.");
        }
        if (normalized.length() > maxLength) {
            throw new ProjectValidationException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private static String requiredEnum(String value, String field, Set<String> allowedValues) {
        String normalized = requiredText(value, field, 40).toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ProjectValidationException(
                    field + " must be one of " + String.join(", ", allowedValues) + ".");
        }
        return normalized;
    }
}
