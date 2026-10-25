package com.pulso.backend.featureflag.application;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;

public class FeatureFlagDisabledException extends RuntimeException {

    public FeatureFlagDisabledException(FeatureFlagKey key) {
        super(String.format(ApiMessages.FEATURE_DISABLED_DETAIL, key.key()));
    }
}
