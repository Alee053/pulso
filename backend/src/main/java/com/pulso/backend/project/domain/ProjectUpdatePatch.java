package com.pulso.backend.project.domain;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.pulso.backend.config.ApiMessages;

/**
 * Editable information for an existing project.
 *
 * Presence is tracked separately from the value so omitted properties can be
 * preserved while nullable editable properties can be cleared explicitly.
 * Protected project attributes are intentionally not represented here.
 */
public record ProjectUpdatePatch(
        String name,
        boolean nameProvided,
        String tagline,
        boolean taglineProvided,
        String description,
        boolean descriptionProvided,
        String valueProposition,
        boolean valuePropositionProvided,
        String stage,
        boolean stageProvided,
        UUID sectorId,
        boolean sectorIdProvided,
        String city,
        boolean cityProvided,
        String department,
        boolean departmentProvided,
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

    /**
     * Convenience constructor for application/domain callers that only need
     * to provide non-null values. JSON callers use the presence-aware form.
     */
    public ProjectUpdatePatch(
            String name,
            String tagline,
            String description,
            String valueProposition,
            String stage,
            UUID sectorId,
            String city,
            String department,
            Limits limits) {
        this(
                name,
                name != null,
                tagline,
                tagline != null,
                description,
                description != null,
                valueProposition,
                valueProposition != null,
                stage,
                stage != null,
                sectorId,
                sectorId != null,
                city,
                city != null,
                department,
                department != null,
                limits);
    }

    public ProjectUpdatePatch {
        name = providedRequiredText(name, "name", limits.nameMaxLength(), nameProvided);
        tagline = providedRequiredText(
                tagline,
                "tagline",
                limits.taglineMaxLength(),
                taglineProvided);
        description = providedOptionalText(
                description,
                "description",
                limits.descriptionMaxLength(),
                descriptionProvided);
        valueProposition = providedOptionalText(
                valueProposition,
                "valueProposition",
                limits.descriptionMaxLength(),
                valuePropositionProvided);
        stage = providedRequiredEnum(stage, "stage", limits.stageTokenMaxLength(), stageProvided);
        if (sectorIdProvided && sectorId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_SECTOR_REQUIRED);
        }
        city = providedRequiredText(city, "city", limits.cityMaxLength(), cityProvided);
        department = providedOptionalText(
                department,
                "department",
                limits.cityMaxLength(),
                departmentProvided);
    }

    private static String providedRequiredText(
            String value,
            String field,
            int maxLength,
            boolean provided) {
        if (!provided) {
            return null;
        }
        if (value == null) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_FIELD_REQUIRED, field));
        }
        return optionalText(value, field, maxLength);
    }

    private static String providedOptionalText(
            String value,
            String field,
            int maxLength,
            boolean provided) {
        if (!provided || value == null) {
            return value;
        }
        return optionalText(value, field, maxLength);
    }

    private static String optionalText(String value, String field, int maxLength) {

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

    private static String providedRequiredEnum(
            String value,
            String field,
            int tokenMaxLength,
            boolean provided) {
        if (!provided) {
            return null;
        }
        if (value == null) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_FIELD_REQUIRED, field));
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
