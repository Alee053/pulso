package com.pulso.backend.project.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.AcceptMemberInvitation;
import com.pulso.backend.project.application.PersistedMemberInvitation;
import com.pulso.backend.project.domain.ProjectValidationException;

@RestController
public class MemberInvitationAcceptanceController {

    private final AcceptMemberInvitation acceptMemberInvitation;

    public MemberInvitationAcceptanceController(AcceptMemberInvitation acceptMemberInvitation) {
        this.acceptMemberInvitation = acceptMemberInvitation;
    }

    @PostMapping("/api/v1/project-members/accept")
    @ResponseStatus(HttpStatus.OK)
    public MemberInvitationResponse accept(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) MemberInvitationAcceptRequest request) {
        if (request == null) {
            throw new ProjectValidationException(ApiMessages.REQUEST_BODY_REQUIRED);
        }
        UUID authenticatedProfileId = authenticatedProfileId(jwt);
        String authenticatedEmail = authenticatedEmail(jwt);
        PersistedMemberInvitation accepted = acceptMemberInvitation.execute(
                authenticatedProfileId,
                authenticatedEmail,
                request.token());
        return MemberInvitationResponse.from(accepted);
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

    private static String authenticatedEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidAuthenticatedSubjectException();
        }
        return email;
    }
}
