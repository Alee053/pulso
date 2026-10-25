package com.pulso.backend.featureflag.application;

import com.pulso.backend.config.ApiMessages;

public class FeatureFlagValidationException extends RuntimeException {

    public FeatureFlagValidationException() {
        super(ApiMessages.FEATURE_FLAG_BOOLEAN_REQUIRED);
    }
}
