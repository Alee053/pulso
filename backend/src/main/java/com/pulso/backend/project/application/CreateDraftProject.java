package com.pulso.backend.project.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.domain.CreatedDraftProject;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class CreateDraftProject {

    private final ProjectRepository repository;
    private final ProjectProperties properties;

    public CreateDraftProject(
            ProjectRepository repository,
            ProjectProperties properties) {
        this.repository = repository;
        this.properties = properties;
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
        if (!repository.hasActiveSector(draft.sectorId())) {
            throw new ProjectValidationException(ApiMessages.SECTOR_MUST_BE_ACTIVE);
        }

        Project project = repository.createDraft(draft, authenticatedProfileId);
        ProjectMember owner = repository.createOwner(project.id(), authenticatedProfileId);
        return new CreatedDraftProject(project, owner);
    }
}
