package com.pulso.backend.featureflag.api;

import com.pulso.backend.featureflag.domain.FeatureFlag;

public record FeatureFlagResponse(String key, boolean enabled) {

    static FeatureFlagResponse from(FeatureFlag featureFlag) {
        return new FeatureFlagResponse(featureFlag.key().key(), featureFlag.enabled());
    }
}
