package com.pulso.backend.featureflag.domain;

public record FeatureFlag(FeatureFlagKey key, boolean enabled) {
}
