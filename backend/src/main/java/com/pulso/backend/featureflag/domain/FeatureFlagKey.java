package com.pulso.backend.featureflag.domain;

import java.util.Arrays;
import java.util.Optional;

public enum FeatureFlagKey {
    LANGUAGE_SWITCHER("language_switcher"),
    PROJECT_CREATION("project_creation");

    private final String key;

    FeatureFlagKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean defaultEnabled() {
        return true;
    }

    public static Optional<FeatureFlagKey> from(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(candidate -> candidate.key.equals(key))
                .findFirst();
    }
}
