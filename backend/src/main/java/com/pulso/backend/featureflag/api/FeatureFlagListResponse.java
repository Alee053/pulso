package com.pulso.backend.featureflag.api;

import java.util.List;

import com.pulso.backend.featureflag.domain.FeatureFlag;

public record FeatureFlagListResponse(List<FeatureFlagResponse> flags) {

    static FeatureFlagListResponse from(List<FeatureFlag> featureFlags) {
        return new FeatureFlagListResponse(featureFlags.stream()
                .map(FeatureFlagResponse::from)
                .toList());
    }
}
