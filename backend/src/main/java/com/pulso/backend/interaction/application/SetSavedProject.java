package com.pulso.backend.interaction.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class SetSavedProject {

    private final ProjectInteractionRepository repository;

    public SetSavedProject(ProjectInteractionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(UUID profileId, UUID projectId, boolean saved) {
        if (profileId == null || projectId == null) {
            throw new ProjectValidationException("profileId and projectId are required.");
        }
        repository.setSaved(profileId, projectId, saved);
    }
}
