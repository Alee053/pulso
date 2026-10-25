package com.pulso.backend.featureflag.application;

import com.pulso.backend.config.ApiMessages;

public class FeatureFlagAuthorizationException extends RuntimeException {

    public FeatureFlagAuthorizationException() {
        super(ApiMessages.FEATURE_FLAG_ADMIN_REQUIRED_DETAIL);
    }
}
