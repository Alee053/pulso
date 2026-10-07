package com.pulso.backend.project.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectMembersProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.ListProjectMembers;
import com.pulso.backend.project.application.ProjectMembersSnapshot;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMembersController {

    private final ListProjectMembers listProjectMembers;
    private final ProjectMembersProperties properties;

    public ProjectMembersController(ListProjectMembers listProjectMembers, ProjectMembersProperties properties) {
        this.listProjectMembers = listProjectMembers;
        this.properties = properties;
    }

    @GetMapping
    public ProjectMembersResponse list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "${pulso.project-members.default-offset}") int offset,
            @RequestParam(defaultValue = "${pulso.project-members.default-limit}") int limit) {
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new InvalidRequestException(String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new InvalidRequestException(String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }
        ProjectMembersSnapshot snapshot = listProjectMembers.execute(
                projectId, authenticatedProfileId(jwt), offset, limit + 1);
        return new ProjectMembersResponse(
                snapshot.members().stream().limit(limit).map(ProjectMembersResponse.MemberResponse::from).toList(),
                new ProjectMembersResponse.ActorResponse(snapshot.actor().role(), snapshot.actor().membershipStatus()),
                new ProjectMembersResponse.PageMetadata(offset, limit, snapshot.members().size() > limit));
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
