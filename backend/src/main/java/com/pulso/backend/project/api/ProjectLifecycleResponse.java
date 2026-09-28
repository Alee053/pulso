package com.pulso.backend.project.api;

import java.util.UUID;

import com.pulso.backend.project.domain.ProjectLifecycleState;

public record ProjectLifecycleResponse(
        UUID projectId,
        String status) {

    public static ProjectLifecycleResponse from(
            UUID projectId,
            ProjectLifecycleState state) {
        return new ProjectLifecycleResponse(projectId, state.status());
    }
}
