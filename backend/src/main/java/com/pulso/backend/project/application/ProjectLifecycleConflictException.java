package com.pulso.backend.project.application;

import com.pulso.backend.config.ApiMessages;

public class ProjectLifecycleConflictException extends RuntimeException {

    public ProjectLifecycleConflictException() {
        super(ApiMessages.PROJECT_LIFECYCLE_CONFLICT_DETAIL);
    }
}
