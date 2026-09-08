package com.pulso.backend.project.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.project.domain.CreatedDraftProject;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class CreateDraftProject {

    private final ProjectRepository repository;

    public CreateDraftProject(ProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreatedDraftProject execute(UUID authenticatedProfileId, ProjectDraft draft) {
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException("authenticatedProfileId is required.");
        }
        if (draft == null) {
            throw new ProjectValidationException("project draft is required.");
        }
        if (!repository.hasActiveSector(draft.sectorId())) {
            throw new ProjectValidationException("sectorId must reference an active sector.");
        }

        Project project = repository.createDraft(draft, authenticatedProfileId);
        ProjectMember owner = repository.createOwner(project.id(), authenticatedProfileId);
        return new CreatedDraftProject(project, owner);
    }
}
