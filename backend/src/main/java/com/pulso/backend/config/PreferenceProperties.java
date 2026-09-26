package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.preferences")
public record PreferenceProperties(
        int maxItemsPerField,
        int maxTokenLength,
        int sectorIdMaxLength) {

    public PreferenceProperties {
        requirePositive("pulso.preferences.max-items-per-field", maxItemsPerField);
        requirePositive("pulso.preferences.max-token-length", maxTokenLength);
        requirePositive("pulso.preferences.sector-id-max-length", sectorIdMaxLength);
    }

    private static void requirePositive(String property, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(property + " must be greater than zero.");
        }
    }
}
