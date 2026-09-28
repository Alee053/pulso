package com.pulso.backend.project.application;

import com.pulso.backend.config.ApiMessages;

public class ProjectLifecycleAuthorizationException extends RuntimeException {

    public ProjectLifecycleAuthorizationException() {
        super(ApiMessages.MEMBERSHIP_PERMISSION_DETAIL);
    }
}
