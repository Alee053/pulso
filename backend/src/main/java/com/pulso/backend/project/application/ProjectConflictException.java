package com.pulso.backend.project.application;

import com.pulso.backend.config.ApiMessages;

public class ProjectConflictException extends RuntimeException {

    public ProjectConflictException() {
        super(ApiMessages.PROJECT_CONFLICT_DETAIL);
    }
}
