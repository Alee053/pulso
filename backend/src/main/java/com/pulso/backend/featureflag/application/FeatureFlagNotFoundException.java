package com.pulso.backend.featureflag.application;

import com.pulso.backend.config.ApiMessages;

public class FeatureFlagNotFoundException extends RuntimeException {

    public FeatureFlagNotFoundException() {
        super(ApiMessages.FEATURE_FLAG_NOT_FOUND_DETAIL);
    }
}
