package com.pulso.backend.profile.application;

import com.pulso.backend.config.ApiMessages;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException() {
        super(ApiMessages.PROFILE_NOT_FOUND_DETAIL);
    }
}
