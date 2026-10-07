package com.pulso.backend.project.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.AdministerProjectMembership;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMembershipController {

    private final AdministerProjectMembership administerProjectMembership;

    public ProjectMembershipController(AdministerProjectMembership administerProjectMembership) {
        this.administerProjectMembership = administerProjectMembership;
    }

    @PatchMapping("/{memberId}")
    @ResponseStatus(HttpStatus.OK)
    public ProjectMemberResponse update(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ProjectMembershipUpdateRequest request) {
        ProjectMemberResponse response = ProjectMemberResponse.from(
                administerProjectMembership.execute(
                        projectId,
                        memberId,
                        authenticatedProfileId(jwt),
                        request == null ? null : request.action(),
                        request == null ? null : request.role()));
        return response;
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
