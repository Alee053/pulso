package com.pulso.backend.project.application;

import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.project.domain.ProjectLifecycleSnapshot;
import com.pulso.backend.project.domain.ProjectLifecycleState;

public interface ProjectLifecycleRepository {

    /**
     * Locks the actor's project before reading its state and membership. Call within
     * the lifecycle transaction so membership administration cannot revoke permissions
     * between this snapshot and the update.
     */
    Optional<ProjectLifecycleSnapshot> findForActor(UUID projectId, UUID profileId);

    int updateLifecycle(
            UUID projectId,
            UUID profileId,
            String expectedStatus,
            ProjectLifecycleState nextState);
}
