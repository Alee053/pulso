package com.pulso.backend.project.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.CreateDraftProject;
import com.pulso.backend.project.domain.CreatedDraftProject;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final CreateDraftProject createDraftProject;

    public ProjectController(CreateDraftProject createDraftProject) {
        this.createDraftProject = createDraftProject;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ProjectCreateRequest request) {
        CreatedDraftProject createdProject = createDraftProject.execute(
                authenticatedProfileId(jwt),
                request == null ? null : request.toDraft(createDraftProject.limits()));
        return ProjectResponse.from(createdProject);
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
