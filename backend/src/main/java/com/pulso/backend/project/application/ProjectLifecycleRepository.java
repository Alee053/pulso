package com.pulso.backend.project.application;

import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.project.domain.ProjectLifecycleSnapshot;
import com.pulso.backend.project.domain.ProjectLifecycleState;

public interface ProjectLifecycleRepository {

    Optional<ProjectLifecycleSnapshot> findForActor(UUID projectId, UUID profileId);

    int updateLifecycle(
            UUID projectId,
            UUID profileId,
            String expectedStatus,
            ProjectLifecycleState nextState);
}
