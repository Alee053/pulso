package com.pulso.backend.interaction.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class GetProjectFeedback {

    private final ProjectInteractionRepository repository;

    public GetProjectFeedback(ProjectInteractionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProjectFeedbackType execute(UUID profileId, UUID projectId) {
        if (profileId == null || projectId == null) {
            throw new ProjectValidationException("profileId and projectId are required.");
        }
        return repository.findFeedback(profileId, projectId);
    }
}
