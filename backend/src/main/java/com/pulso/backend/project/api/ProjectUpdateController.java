package com.pulso.backend.project.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.UpdateProjectInformation;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectUpdateController {

    private final UpdateProjectInformation updateProjectInformation;

    public ProjectUpdateController(UpdateProjectInformation updateProjectInformation) {
        this.updateProjectInformation = updateProjectInformation;
    }

    @PatchMapping("/{projectId}")
    public ProjectUpdateResponse update(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProjectUpdateRequest request) {
        return ProjectUpdateResponse.from(updateProjectInformation.execute(
                projectId,
                authenticatedProfileId(jwt),
                request.toPatch(updateProjectInformation.limits())));
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
