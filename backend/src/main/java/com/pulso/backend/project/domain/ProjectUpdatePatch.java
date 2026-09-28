package com.pulso.backend.project.domain;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.pulso.backend.config.ApiMessages;

/**
 * Editable information for an existing project.
 *
 * A null value means that the corresponding JSON property was omitted. The
 * repository therefore keeps the current value for that field. Protected
 * project attributes are intentionally not represented here.
 */
public record ProjectUpdatePatch(
        String name,
        String tagline,
        String description,
        String valueProposition,
        String stage,
        UUID sectorId,
        String city,
        String department,
        Limits limits) {

    private static final Set<String> ALLOWED_STAGES = Set.of(
            "IDEA",
            "PROTOTYPE",
            "MVP",
            "EARLY_STAGE",
            "GROWING",
            "ESTABLISHED");

    public record Limits(
            int nameMaxLength,
            int taglineMaxLength,
            int descriptionMaxLength,
            int cityMaxLength,
            int stageTokenMaxLength) {
    }

    public ProjectUpdatePatch {
        name = optionalText(name, "name", limits.nameMaxLength());
        tagline = optionalText(tagline, "tagline", limits.taglineMaxLength());
        description = optionalText(description, "description", limits.descriptionMaxLength());
        valueProposition = optionalText(
                valueProposition,
                "valueProposition",
                limits.descriptionMaxLength());
        stage = optionalEnum(stage, "stage", limits.stageTokenMaxLength());
        city = optionalText(city, "city", limits.cityMaxLength());
        department = optionalText(department, "department", limits.cityMaxLength());
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

    private static String optionalEnum(String value, String field, int tokenMaxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_FIELD_BLANK, field));
        }
        if (normalized.length() > tokenMaxLength) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_FIELD_MAX_LENGTH, field, tokenMaxLength));
        }
        if (!ALLOWED_STAGES.contains(normalized)) {
            throw new ProjectValidationException(
                    String.format(
                            ApiMessages.PROJECT_FIELD_ENUM,
                            field,
                            String.join(", ", ALLOWED_STAGES)));
        }
        return normalized;
    }
}
