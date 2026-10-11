package com.pulso.backend.project.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pulso.backend.project.domain.ProjectShareLink;

@Service
public class GenerateProjectShareLink {

    private final PublicProjectRepository publicProjectRepository;
    private final String publicBaseUrl;

    public GenerateProjectShareLink(
            PublicProjectRepository publicProjectRepository,
            @Value("${pulso.public-base-url:http://localhost:4200}") String publicBaseUrl) {
        this.publicProjectRepository = publicProjectRepository;
        this.publicBaseUrl = publicBaseUrl;
    }

    public ProjectShareLink execute(String projectRef) {
        var project = publicProjectRepository.findPublishedByReference(projectRef)
                .orElseThrow(PublicProjectNotFoundException::new);

        String url = publicBaseUrl + "/proyectos/" + project.slug();
        return new ProjectShareLink(project.id(), project.slug(), url);
    }
}