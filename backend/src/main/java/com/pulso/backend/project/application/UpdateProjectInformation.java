package com.pulso.backend.project.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.ProjectValidationException;
import com.pulso.backend.project.domain.UpdatedProject;

@Service
public class UpdateProjectInformation {

    private final ProjectUpdateRepository repository;
    private final ProjectProperties properties;

    public UpdateProjectInformation(
            ProjectUpdateRepository repository,
            ProjectProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public ProjectUpdatePatch.Limits limits() {
        return new ProjectUpdatePatch.Limits(
                properties.nameMaxLength(),
                properties.taglineMaxLength(),
                properties.descriptionMaxLength(),
                properties.cityMaxLength(),
                properties.stageTokenMaxLength());
    }

    @Transactional
    public UpdatedProject execute(
            UUID projectId,
            UUID authenticatedProfileId,
            ProjectUpdatePatch patch) {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
        if (patch == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_UPDATE_REQUIRED);
        }
        if (patch.sectorId() != null && !repository.hasActiveSector(patch.sectorId())) {
            throw new ProjectValidationException(ApiMessages.SECTOR_MUST_BE_ACTIVE);
        }

        return repository.updateInformation(projectId, authenticatedProfileId, patch)
                .orElseThrow(ProjectUpdateAuthorizationException::new);
    }
}
