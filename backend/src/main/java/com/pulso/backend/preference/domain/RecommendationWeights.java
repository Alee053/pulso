package com.pulso.backend.preference.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.pulso.backend.config.ApiMessages;

public final class RecommendationWeights {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int MAX_ENTRIES = 50;
    private static final int MAX_KEY_LENGTH = 80;

    private RecommendationWeights() {
    }

    public static Map<String, Double> normalize(Map<String, Double> weights) {
        if (weights == null) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.PREFERENCES_WEIGHTS_INVALID);
        }
        if (weights.size() > MAX_ENTRIES) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.PREFERENCES_WEIGHTS_TOO_MANY);
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String rawKey = entry.getKey();
            Double weight = entry.getValue();
            if (rawKey == null || weight == null) {
                throw new OnboardingPreferencesValidationException(
                        ApiMessages.PREFERENCES_WEIGHTS_INVALID);
            }

            String key = rawKey.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()
                    || key.length() > MAX_KEY_LENGTH
                    || !KEY_PATTERN.matcher(key).matches()
                    || !Double.isFinite(weight)
                    || weight < -100.0
                    || weight > 100.0
                    || normalized.putIfAbsent(key, weight) != null) {
                throw new OnboardingPreferencesValidationException(
                        ApiMessages.PREFERENCES_WEIGHTS_INVALID);
            }
        }
        return Map.copyOf(normalized);
    }
}
