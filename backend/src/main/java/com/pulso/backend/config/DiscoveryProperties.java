package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.discovery")
public record DiscoveryProperties(
        int defaultOffset,
        int defaultLimit,
        int maxLimit,
        int maxOffset) {

    public DiscoveryProperties {
        requireNonNegative("pulso.discovery.default-offset", defaultOffset);
        requirePositive("pulso.discovery.default-limit", defaultLimit);
        requirePositive("pulso.discovery.max-limit", maxLimit);
        requireNonNegative("pulso.discovery.max-offset", maxOffset);

        if (defaultOffset > maxOffset) {
            throw new IllegalArgumentException(
                    "pulso.discovery.default-offset must not exceed pulso.discovery.max-offset.");
        }
        if (defaultLimit > maxLimit) {
            throw new IllegalArgumentException(
                    "pulso.discovery.default-limit must not exceed pulso.discovery.max-limit.");
        }
    }

    private static void requirePositive(String property, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(property + " must be greater than zero.");
        }
    }

    private static void requireNonNegative(String property, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(property + " must not be negative.");
        }
    }
}
