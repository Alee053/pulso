package com.pulso.backend.project.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.featureflag.application.FeatureFlagDisabledException;
import com.pulso.backend.featureflag.application.FeatureFlagReader;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;
import com.pulso.backend.project.domain.CreatedDraftProject;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class CreateDraftProject {

    private final ProjectRepository repository;
    private final ProjectProperties properties;
    private final FeatureFlagReader featureFlags;

    public CreateDraftProject(
            ProjectRepository repository,
            ProjectProperties properties,
            FeatureFlagReader featureFlags) {
        this.repository = repository;
        this.properties = properties;
        this.featureFlags = featureFlags;
    }

    public ProjectDraft.Limits limits() {
        return new ProjectDraft.Limits(
                properties.slugMinLength(),
                properties.slugMaxLength(),
                properties.nameMaxLength(),
                properties.taglineMaxLength(),
                properties.descriptionMaxLength(),
                properties.cityMaxLength(),
                properties.stageTokenMaxLength());
    }

    @Transactional
    public CreatedDraftProject execute(UUID authenticatedProfileId, ProjectDraft draft) {
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
        if (draft == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_DRAFT_REQUIRED);
        }
        if (!featureFlags.isEnabled(FeatureFlagKey.PROJECT_CREATION)) {
            throw new FeatureFlagDisabledException(FeatureFlagKey.PROJECT_CREATION);
        }
        if (!repository.hasActiveSector(draft.sectorId())) {
            throw new ProjectValidationException(ApiMessages.SECTOR_MUST_BE_ACTIVE);
        }

        Project project = repository.createDraft(draft, authenticatedProfileId);
        ProjectMember owner = repository.createOwner(project.id(), authenticatedProfileId);
        return new CreatedDraftProject(project, owner);
    }
}
