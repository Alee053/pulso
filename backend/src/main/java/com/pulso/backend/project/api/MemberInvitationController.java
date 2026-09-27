package com.pulso.backend.project.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.AcceptMemberInvitation;
import com.pulso.backend.project.application.CreateMemberInvitation;
import com.pulso.backend.project.domain.ProjectValidationException;

@RestController
public class MemberInvitationController {
    private final CreateMemberInvitation create;
    private final AcceptMemberInvitation accept;
    public MemberInvitationController(CreateMemberInvitation create, AcceptMemberInvitation accept) {
        this.create = create; this.accept = accept;
    }
    @PostMapping("/api/v1/projects/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberInvitationResponse create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId, @RequestBody(required = false) MemberInvitationCreateRequest body) {
        if (body == null) throw new ProjectValidationException("request body is required.");
        return MemberInvitationResponse.from(create.execute(subject(jwt), projectId, body.inviteeEmail()));
    }
    @PostMapping("/api/v1/project-members/accept")
    public MemberInvitationResponse accept(@AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) MemberInvitationAcceptRequest body) {
        if (body == null) throw new ProjectValidationException("request body is required.");
        UUID user = subject(jwt);
        // Only the signed Supabase JWT is trusted for identity, never the request body.
        var accepted = accept.execute(user, jwt.getClaimAsString("email"), body.token());
        return MemberInvitationResponse.from(accepted.id(), accepted.invitation());
    }
    private UUID subject(Jwt jwt) {
        if (jwt == null) throw new InvalidAuthenticatedSubjectException();
        try { return UUID.fromString(jwt.getSubject()); }
        catch (RuntimeException ex) { throw new InvalidAuthenticatedSubjectException(); }
    }
}