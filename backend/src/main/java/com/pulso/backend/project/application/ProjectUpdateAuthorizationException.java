package com.pulso.backend.project.application;

import com.pulso.backend.config.ApiMessages;

public class ProjectUpdateAuthorizationException extends RuntimeException {

    public ProjectUpdateAuthorizationException() {
        super(ApiMessages.MEMBERSHIP_PERMISSION_DETAIL);
    }
}
