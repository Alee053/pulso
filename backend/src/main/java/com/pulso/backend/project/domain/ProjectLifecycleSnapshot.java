package com.pulso.backend.project.domain;

import java.util.UUID;

import com.pulso.backend.config.ApiMessages;

public record ProjectLifecycleSnapshot(
        UUID projectId,
        UUID profileId,
        ProjectLifecycleState state,
        ProjectLifecycleActor actor) {

    public ProjectLifecycleSnapshot {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (profileId == null) {
            throw new ProjectValidationException(ApiMessages.PROFILE_ID_REQUIRED);
        }
        if (state == null) {
            throw new ProjectValidationException(ApiMessages.LIFECYCLE_STATE_REQUIRED);
        }
        if (actor == null) {
            throw new ProjectLifecyclePermissionException(
                    ApiMessages.MEMBERSHIP_PERMISSION_DETAIL);
        }
    }
}
