package com.pulso.backend.project.domain;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.pulso.backend.config.ApiMessages;

public record ProjectDraft(
        String slug,
        String name,
        String tagline,
        String description,
        UUID sectorId,
        String city,
        String stage,
        Limits limits) {

    private static final Set<String> ALLOWED_STAGES = Set.of(
            "IDEA",
            "PROTOTYPE",
            "MVP",
            "EARLY_STAGE",
            "GROWING",
            "ESTABLISHED");

    public record Limits(
            int slugMinLength,
            int slugMaxLength,
            int nameMaxLength,
            int taglineMaxLength,
            int descriptionMaxLength,
            int cityMaxLength,
            int stageTokenMaxLength) {
    }

    public ProjectDraft {
        slug = requiredSlug(slug, limits);
        name = requiredText(name, "name", limits.nameMaxLength());
        tagline = requiredText(tagline, "tagline", limits.taglineMaxLength());
        description = optionalText(description, "description", limits.descriptionMaxLength());
        city = requiredText(city, "city", limits.cityMaxLength());
        stage = requiredEnum(stage, "stage", ALLOWED_STAGES, limits.stageTokenMaxLength());
    }

    private static String requiredSlug(String slug, Limits limits) {
        String normalized = requiredText(slug, "slug", limits.slugMaxLength())
                .toLowerCase(Locale.ROOT);
        if (normalized.length() < limits.slugMinLength()) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_SLUG_MIN_LENGTH, limits.slugMinLength()));
        }
        return normalized;
    }

    private static String requiredText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_FIELD_REQUIRED, field));
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_FIELD_BLANK, field));
        }
        if (normalized.length() > maxLength) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_FIELD_MAX_LENGTH, field, maxLength));
        }
        return normalized;
    }

    private static String requiredEnum(
            String value,
            String field,
            Set<String> allowedValues,
            int tokenMaxLength) {
        String normalized = requiredText(value, field, tokenMaxLength).toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ProjectValidationException(
                    String.format(
                            ApiMessages.PROJECT_FIELD_ENUM,
                            field,
                            String.join(", ", allowedValues)));
        }
        return normalized;
    }
}
