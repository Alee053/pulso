package com.pulso.backend.project.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.ChangeProjectLifecycle;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectLifecycleController {

    private final ChangeProjectLifecycle changeProjectLifecycle;

    public ProjectLifecycleController(ChangeProjectLifecycle changeProjectLifecycle) {
        this.changeProjectLifecycle = changeProjectLifecycle;
    }

    @PostMapping("/{projectId}/publication-request")
    @ResponseStatus(HttpStatus.OK)
    public ProjectLifecycleResponse requestPublication(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ProjectLifecycleActionRequest ignoredRequest) {
        return ProjectLifecycleResponse.from(
                projectId,
                changeProjectLifecycle.requestPublication(projectId, authenticatedProfileId(jwt)));
    }

    @PostMapping("/{projectId}/archive")
    @ResponseStatus(HttpStatus.OK)
    public ProjectLifecycleResponse archive(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ProjectLifecycleActionRequest ignoredRequest) {
        return ProjectLifecycleResponse.from(
                projectId,
                changeProjectLifecycle.archive(projectId, authenticatedProfileId(jwt)));
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
