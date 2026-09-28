package com.pulso.backend.project.application;

import org.springframework.stereotype.Service;

import com.pulso.backend.project.domain.PublicProjectDetail;

@Service
public class GetPublicProjectDetail {

    private final PublicProjectRepository repository;

    public GetPublicProjectDetail(PublicProjectRepository repository) {
        this.repository = repository;
    }

    public PublicProjectDetail execute(String projectRef) {
        if (projectRef == null || projectRef.isBlank()) {
            throw new PublicProjectNotFoundException();
        }

        return repository.findPublishedByReference(projectRef)
                .orElseThrow(PublicProjectNotFoundException::new);
    }
}
