package com.pulso.backend.project.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.project.domain.ProjectLifecycle;
import com.pulso.backend.project.domain.ProjectLifecyclePermissionException;
import com.pulso.backend.project.domain.ProjectLifecycleSnapshot;
import com.pulso.backend.project.domain.ProjectLifecycleState;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class ChangeProjectLifecycle {

    private final ProjectLifecycleRepository repository;
    private final Clock clock;

    @Autowired
    public ChangeProjectLifecycle(ProjectLifecycleRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public ChangeProjectLifecycle(ProjectLifecycleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ProjectLifecycleState requestPublication(UUID projectId, UUID authenticatedProfileId) {
        ProjectLifecycleSnapshot snapshot = loadAuthorizedSnapshot(
                projectId,
                authenticatedProfileId);
        ProjectLifecycleState nextState = applyRequestPublication(snapshot);
        persist(projectId, authenticatedProfileId, snapshot.state(), nextState);
        return nextState;
    }

    @Transactional
    public ProjectLifecycleState archive(UUID projectId, UUID authenticatedProfileId) {
        ProjectLifecycleSnapshot snapshot = loadAuthorizedSnapshot(
                projectId,
                authenticatedProfileId);
        ProjectLifecycleState nextState = applyArchive(snapshot);
        persist(projectId, authenticatedProfileId, snapshot.state(), nextState);
        return nextState;
    }

    private ProjectLifecycleSnapshot loadAuthorizedSnapshot(
            UUID projectId,
            UUID authenticatedProfileId) {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }

        return repository.findForActor(projectId, authenticatedProfileId)
                .filter(snapshot -> projectId.equals(snapshot.projectId()))
                .filter(snapshot -> authenticatedProfileId.equals(snapshot.profileId()))
                .orElseThrow(ProjectLifecycleAuthorizationException::new);
    }

    private static ProjectLifecycleState applyRequestPublication(ProjectLifecycleSnapshot snapshot) {
        try {
            return ProjectLifecycle.requestPublication(snapshot.state(), snapshot.actor());
        } catch (ProjectLifecyclePermissionException exception) {
            throw new ProjectLifecycleAuthorizationException();
        }
    }

    private ProjectLifecycleState applyArchive(ProjectLifecycleSnapshot snapshot) {
        try {
            return ProjectLifecycle.archive(
                    snapshot.state(),
                    snapshot.actor(),
                    Instant.now(clock));
        } catch (ProjectLifecyclePermissionException exception) {
            throw new ProjectLifecycleAuthorizationException();
        }
    }

    private void persist(
            UUID projectId,
            UUID authenticatedProfileId,
            ProjectLifecycleState currentState,
            ProjectLifecycleState nextState) {
        if (repository.updateLifecycle(
                projectId,
                authenticatedProfileId,
                currentState.status(),
                nextState) != 1) {
            throw new ProjectLifecycleConflictException();
        }
    }
}
