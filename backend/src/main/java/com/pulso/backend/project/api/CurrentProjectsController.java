package com.pulso.backend.project.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.CurrentProjectsProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.ListCurrentProjects;
import com.pulso.backend.project.domain.CurrentProject;

@RestController
@RequestMapping("/api/v1/me/projects")
public class CurrentProjectsController {
    private final ListCurrentProjects listCurrentProjects;
    private final CurrentProjectsProperties properties;

    public CurrentProjectsController(ListCurrentProjects listCurrentProjects, CurrentProjectsProperties properties) {
        this.listCurrentProjects = listCurrentProjects;
        this.properties = properties;
    }

    @GetMapping
    public CurrentProjectsResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "${pulso.current-projects.default-offset}") int offset,
            @RequestParam(defaultValue = "${pulso.current-projects.default-limit}") int limit) {
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new InvalidRequestException(String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new InvalidRequestException(String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }
        List<CurrentProject> projects = listCurrentProjects.execute(authenticatedProfileId(jwt), offset, limit + 1);
        return new CurrentProjectsResponse(projects.stream().limit(limit).toList(),
                new CurrentProjectsResponse.PageMetadata(offset, limit, projects.size() > limit));
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

    public record CurrentProjectsResponse(List<CurrentProject> data, PageMetadata meta) {
        public record PageMetadata(int offset, int limit, boolean hasMore) {
        }
    }
}
