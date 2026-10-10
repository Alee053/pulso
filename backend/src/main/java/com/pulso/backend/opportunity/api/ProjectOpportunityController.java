package com.pulso.backend.opportunity.api;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ProjectOpportunityProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.opportunity.application.ManageProjectOpportunities;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/opportunities")
public class ProjectOpportunityController {

    private final ManageProjectOpportunities manageProjectOpportunities;
    private final ProjectOpportunityProperties properties;

    @Autowired
    public ProjectOpportunityController(
            ManageProjectOpportunities manageProjectOpportunities,
            ProjectOpportunityProperties properties) {
        this.manageProjectOpportunities = manageProjectOpportunities;
        this.properties = properties;
    }

    @GetMapping
    public ProjectOpportunityPageResponse list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        return ProjectOpportunityPageResponse.from(manageProjectOpportunities.list(
                projectId,
                authenticatedProfileId(jwt),
                offset == null ? properties.defaultOffset() : offset,
                limit == null ? properties.defaultLimit() : limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectOpportunityResponse create(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateProjectOpportunityRequest request) {
        if (request == null) {
            throw new InvalidRequestException("The request body is required.");
        }
        return ProjectOpportunityResponse.from(manageProjectOpportunities.create(
                projectId,
                authenticatedProfileId(jwt),
                request.opportunityType(),
                request.title(),
                request.description(),
                request.city(),
                request.department(),
                request.remoteAllowed(),
                request.status()));
    }

    @PutMapping("/{opportunityId}")
    public ProjectOpportunityResponse edit(
            @PathVariable UUID projectId,
            @PathVariable UUID opportunityId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody EditProjectOpportunityRequest request) {
        if (request == null) {
            throw new InvalidRequestException("The request body is required.");
        }
        return ProjectOpportunityResponse.from(manageProjectOpportunities.edit(
                projectId,
                opportunityId,
                authenticatedProfileId(jwt),
                request.opportunityType(),
                request.title(),
                request.description(),
                request.city(),
                request.department(),
                request.remoteAllowed()));
    }

    @PostMapping("/{opportunityId}/actions")
    public ProjectOpportunityResponse changeStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID opportunityId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProjectOpportunityActionRequest request) {
        if (request == null) {
            throw new InvalidRequestException("The request body is required.");
        }
        return ProjectOpportunityResponse.from(manageProjectOpportunities.changeStatus(
                projectId,
                opportunityId,
                authenticatedProfileId(jwt),
                request.action()));
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
