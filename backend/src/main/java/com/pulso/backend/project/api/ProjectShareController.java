package com.pulso.backend.project.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.interaction.application.ProjectInteractionRepository;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.GenerateProjectShareLink;
import com.pulso.backend.project.application.PublicProjectNotFoundException;
import com.pulso.backend.project.application.PublicProjectRepository;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectShareController {

    private final PublicProjectRepository publicProjectRepository;
    private final ProjectInteractionRepository interactionRepository;
    private final GenerateProjectShareLink generateProjectShareLink;

    public ProjectShareController(
            PublicProjectRepository publicProjectRepository,
            ProjectInteractionRepository interactionRepository,
            GenerateProjectShareLink generateProjectShareLink) {
        this.publicProjectRepository = publicProjectRepository;
        this.interactionRepository = interactionRepository;
        this.generateProjectShareLink = generateProjectShareLink;
    }

    @GetMapping("/{projectRef}/share")
    public ProjectShareResponse generate(@PathVariable String projectRef) {
        return ProjectShareResponse.from(generateProjectShareLink.execute(projectRef));
    }

    @PostMapping("/{projectRef}/share")
    public ProjectShareResponse register(
            @PathVariable String projectRef,
            @RequestBody ProjectShareRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        var project = publicProjectRepository.findPublishedByReference(projectRef)
                .orElseThrow(PublicProjectNotFoundException::new);

        UUID profileId = authenticatedProfileId(jwt);
        interactionRepository.insertSharedEvent(profileId, project.id(), request.origin());

        return ProjectShareResponse.from(generateProjectShareLink.execute(projectRef));
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidAuthenticatedSubjectException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }
}