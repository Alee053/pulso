package com.pulso.backend.featureflag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeatureFlagKeyTest {

    @Test
    void exposesOnlyTheInitialFlagsAsEnabledByDefault() {
        assertThat(FeatureFlagKey.values())
                .containsExactly(
                        FeatureFlagKey.LANGUAGE_SWITCHER,
                        FeatureFlagKey.PROJECT_CREATION);
        assertThat(FeatureFlagKey.values())
                .allSatisfy(key -> assertThat(key.defaultEnabled()).isTrue());
    }

    @Test
    void acceptsOnlyExactKnownKeys() {
        assertThat(FeatureFlagKey.from("language_switcher"))
                .contains(FeatureFlagKey.LANGUAGE_SWITCHER);
        assertThat(FeatureFlagKey.from("project_creation"))
                .contains(FeatureFlagKey.PROJECT_CREATION);
        assertThat(FeatureFlagKey.from("project_creation_extra")).isEmpty();
        assertThat(FeatureFlagKey.from(null)).isEmpty();
    }
}
