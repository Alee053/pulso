package com.pulso.backend.preference.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.pulso.backend.config.ApiMessages;

public record OnboardingPreferences(
        UUID profileId,
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages,
        List<String> preferredTags,
        List<ProfileSkill> skills,
        Map<String, Double> recommendationWeights,
        OffsetDateTime onboardingCompletedAt,
        Limits limits) {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> ALLOWED_INTENTION_SET = Set.copyOf(OnboardingOptions.INTENTIONS);
    private static final Set<String> ALLOWED_STAGE_SET = Set.copyOf(OnboardingOptions.STAGES);

    public record Limits(
            int maxItemsPerField,
            int maxTokenLength,
            int sectorIdMaxLength) {
    }

    public OnboardingPreferences {
        if (profileId == null) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.PREFERENCES_REQUIRED_PROFILE);
        }
        intentions = normalizeList(
                intentions,
                "intentions",
                limits.maxItemsPerField(),
                value -> normalizeKnownToken(
                        value,
                        "intentions",
                        ALLOWED_INTENTION_SET,
                        limits.maxTokenLength()));
        preferredSectors = normalizeList(
                preferredSectors,
                "preferredSectors",
                limits.maxItemsPerField(),
                value -> normalizeSectorReference(value, limits.sectorIdMaxLength()));
        preferredStages = normalizeList(
                preferredStages,
                "preferredStages",
                limits.maxItemsPerField(),
                value -> normalizeKnownToken(
                        value,
                        "preferredStages",
                        ALLOWED_STAGE_SET,
                        limits.maxTokenLength()));
        preferredTags = normalizeList(
                preferredTags,
                "preferredTags",
                limits.maxItemsPerField(),
                value -> normalizeCatalogId(value, "preferredTags"));
        skills = normalizeSkills(skills, limits.maxItemsPerField());
        recommendationWeights = RecommendationWeights.normalize(recommendationWeights);
    }

    public OnboardingPreferences(
            UUID profileId,
            List<String> intentions,
            List<String> preferredSectors,
            List<String> preferredStages,
            List<String> preferredTags,
            List<String> preferredSkills,
            Map<String, Double> recommendationWeights,
            Limits limits) {
        this(profileId, intentions, preferredSectors, preferredStages, preferredTags,
                legacySkills(preferredSkills), recommendationWeights, null, limits);
    }

    public List<String> preferredSkills() {
        return skills.stream().map(ProfileSkill::skillId).toList();
    }

    public void requireCompleteSelections() {
        if (intentions.isEmpty()) {
            throw new OnboardingPreferencesValidationException(ApiMessages.ONBOARDING_INTENTION_REQUIRED);
        }
        if (skills.stream().anyMatch(skill -> skill.proficiency() == null)) {
            throw new OnboardingPreferencesValidationException(ApiMessages.SKILL_PROFICIENCY_REQUIRED);
        }
    }

    private static List<ProfileSkill> legacySkills(List<String> skillIds) {
        if (skillIds == null) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_MUST_BE_ARRAY, "preferredSkills"));
        }
        return skillIds.stream().map(id -> new ProfileSkill(id, null)).toList();
    }

    private static List<ProfileSkill> normalizeSkills(List<ProfileSkill> skills, int maxItems) {
        if (skills == null) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_MUST_BE_ARRAY, "skills"));
        }
        if (skills.size() > maxItems) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_MAX_ITEMS, "skills", maxItems));
        }
        Set<String> seen = new HashSet<>();
        for (ProfileSkill skill : skills) {
            if (skill == null) {
                throw new OnboardingPreferencesValidationException(ApiMessages.SKILL_SELECTION_REQUIRED);
            }
            if (!seen.add(skill.skillId())) {
                throw new OnboardingPreferencesValidationException(
                        String.format(ApiMessages.PREFERENCES_DUPLICATE, "skills", skill.skillId()));
            }
        }
        return List.copyOf(skills);
    }

    public OnboardingPreferences(
            UUID profileId,
            List<String> intentions,
            List<String> preferredSectors,
            List<String> preferredStages,
            Limits limits) {
        this(profileId, intentions, preferredSectors, preferredStages, List.of(), List.of(), Map.of(), limits);
    }

    public static OnboardingPreferences empty(UUID profileId, Limits limits) {
        return new OnboardingPreferences(
                profileId,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                limits);
    }

    private static String normalizeCatalogId(String value, String field) {
        String trimmed = value.trim();
        if (!UUID_PATTERN.matcher(trimmed).matches()) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_CATALOG_ID_INVALID, field));
        }
        try {
            return UUID.fromString(trimmed).toString();
        } catch (IllegalArgumentException exception) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_CATALOG_ID_INVALID, field));
        }
    }

    private static List<String> normalizeList(
            List<String> values,
            String field,
            int maxItemsPerField,
            Function<String, String> normalizer) {
        if (values == null) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_MUST_BE_ARRAY, field));
        }
        if (values.size() > maxItemsPerField) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_MAX_ITEMS, field, maxItemsPerField));
        }

        List<String> normalizedValues = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new OnboardingPreferencesValidationException(
                        String.format(ApiMessages.PREFERENCES_STRINGS_ONLY, field));
            }

            String normalized = normalizer.apply(value);
            if (!seen.add(normalized)) {
                throw new OnboardingPreferencesValidationException(
                        String.format(ApiMessages.PREFERENCES_DUPLICATE, field, normalized));
            }
            normalizedValues.add(normalized);
        }
        return List.copyOf(normalizedValues);
    }

    private static String normalizeKnownToken(
            String value,
            String field,
            Set<String> allowedValues,
            int maxTokenLength) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_BLANK_VALUES, field));
        }
        if (trimmed.length() > maxTokenLength) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_INVALID_VALUE, field));
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_UNKNOWN_VALUE, field, normalized));
        }
        return normalized;
    }

    private static String normalizeSectorReference(String value, int sectorIdMaxLength) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.PREFERENCES_SECTORS_BLANK);
        }
        if (normalized.length() > sectorIdMaxLength) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.PREFERENCES_SECTORS_INVALID);
        }
        if (UUID_PATTERN.matcher(normalized).matches()) {
            return UUID.fromString(normalized).toString();
        }
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new OnboardingPreferencesValidationException(
                    String.format(
                            ApiMessages.PREFERENCES_SECTORS_INVALID_VALUE,
                            value.trim()));
        }
        return normalized;
    }
}
