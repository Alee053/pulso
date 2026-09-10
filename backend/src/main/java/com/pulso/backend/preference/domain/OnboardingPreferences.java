package com.pulso.backend.preference.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

public record OnboardingPreferences(
        UUID profileId,
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages) {

    private static final int MAX_ITEMS_PER_FIELD = 10;
    private static final int MAX_TOKEN_LENGTH = 40;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> ALLOWED_INTENTION_SET = Set.of(
            "DISCOVER",
            "BUY",
            "COLLABORATE",
            "WORK",
            "INVEST",
            "FIND_PARTNERS",
            "FIND_CUSTOMERS",
            "FIND_PROVIDERS",
            "EXPLORE_ECOSYSTEM");
    private static final Set<String> ALLOWED_STAGE_SET = Set.of(
            "IDEA",
            "PROTOTYPE",
            "MVP",
            "EARLY_STAGE",
            "GROWING",
            "ESTABLISHED");

    public OnboardingPreferences {
        if (profileId == null) {
            throw new OnboardingPreferencesValidationException("profileId is required.");
        }
        intentions = normalizeList(
                intentions,
                "intentions",
                value -> normalizeKnownToken(value, "intentions", ALLOWED_INTENTION_SET));
        preferredSectors = normalizeList(
                preferredSectors,
                "preferredSectors",
                OnboardingPreferences::normalizeSectorReference);
        preferredStages = normalizeList(
                preferredStages,
                "preferredStages",
                value -> normalizeKnownToken(value, "preferredStages", ALLOWED_STAGE_SET));
    }

    public static OnboardingPreferences empty(UUID profileId) {
        return new OnboardingPreferences(profileId, List.of(), List.of(), List.of());
    }

    private static List<String> normalizeList(
            List<String> values,
            String field,
            Function<String, String> normalizer) {
        if (values == null) {
            throw new OnboardingPreferencesValidationException(field + " must be an array.");
        }
        if (values.size() > MAX_ITEMS_PER_FIELD) {
            throw new OnboardingPreferencesValidationException(
                    field + " must not contain more than " + MAX_ITEMS_PER_FIELD + " items.");
        }

        List<String> normalizedValues = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new OnboardingPreferencesValidationException(
                        field + " must contain strings only.");
            }

            String normalized = normalizer.apply(value);
            if (!seen.add(normalized)) {
                throw new OnboardingPreferencesValidationException(
                        field + " must not contain duplicates: '" + normalized + "'.");
            }
            normalizedValues.add(normalized);
        }
        return List.copyOf(normalizedValues);
    }

    private static String normalizeKnownToken(
            String value,
            String field,
            Set<String> allowedValues) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new OnboardingPreferencesValidationException(
                    field + " must not contain blank values.");
        }
        if (trimmed.length() > MAX_TOKEN_LENGTH) {
            throw new OnboardingPreferencesValidationException(
                    field + " contains an invalid value.");
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new OnboardingPreferencesValidationException(
                    field + " contains unknown value '" + normalized + "'.");
        }
        return normalized;
    }

    private static String normalizeSectorReference(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new OnboardingPreferencesValidationException(
                    "preferredSectors must not contain blank values.");
        }
        if (normalized.length() > 120) {
            throw new OnboardingPreferencesValidationException(
                    "preferredSectors contains an invalid identifier.");
        }
        if (UUID_PATTERN.matcher(normalized).matches()) {
            return UUID.fromString(normalized).toString();
        }
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new OnboardingPreferencesValidationException(
                    "preferredSectors contains an invalid identifier '" + value.trim() + "'.");
        }
        return normalized;
    }
}
