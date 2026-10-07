package com.pulso.backend.project.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectMembershipsProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.AdministerProjectMembership;
import com.pulso.backend.project.application.ListProjectMemberships;
import com.pulso.backend.project.domain.ManagedProjectMember;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMembershipController {

    private final AdministerProjectMembership administerProjectMembership;
    private final ListProjectMemberships listProjectMemberships;
    private final ProjectMembershipsProperties properties;

    public ProjectMembershipController(
            AdministerProjectMembership administerProjectMembership,
            ListProjectMemberships listProjectMemberships,
            ProjectMembershipsProperties properties) {
        this.administerProjectMembership = administerProjectMembership;
        this.listProjectMemberships = listProjectMemberships;
        this.properties = properties;
    }

    @GetMapping
    public ProjectMembershipListResponse list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        int pageOffset = offset == null ? properties.defaultOffset() : offset;
        int pageLimit = limit == null ? properties.defaultLimit() : limit;
        if (pageOffset < 0 || pageOffset > properties.maxOffset()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (pageLimit < 1 || pageLimit > properties.maxLimit()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }

        List<ManagedProjectMember> members = listProjectMemberships.execute(
                projectId, authenticatedProfileId(jwt), pageOffset, pageLimit + 1);
        return new ProjectMembershipListResponse(
                members.stream().limit(pageLimit).map(ManagedProjectMemberResponse::from).toList(),
                new ProjectMembershipListResponse.PageMetadata(
                        pageOffset, pageLimit, members.size() > pageLimit));
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
