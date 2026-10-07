package com.pulso.backend.project.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pulso.backend.project.domain.CurrentProject;

@Service
public class ListCurrentProjects {
    private final CurrentProjectsRepository repository;

    public ListCurrentProjects(CurrentProjectsRepository repository) {
        this.repository = repository;
    }

    public List<CurrentProject> execute(UUID authenticatedProfileId, int offset, int limit) {
        return repository.findActive(authenticatedProfileId, offset, limit);
    }
}
