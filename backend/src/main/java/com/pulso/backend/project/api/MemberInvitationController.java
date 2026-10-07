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
import com.pulso.backend.project.application.CreateMemberInvitation;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class MemberInvitationController {

    private final CreateMemberInvitation createMemberInvitation;

    public MemberInvitationController(CreateMemberInvitation createMemberInvitation) {
        this.createMemberInvitation = createMemberInvitation;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberInvitationResponse create(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) MemberInvitationCreateRequest request) {
        return MemberInvitationResponse.from(createMemberInvitation.execute(
                projectId,
                authenticatedProfileId(jwt),
                request == null ? null : request.inviteeEmail()));
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
