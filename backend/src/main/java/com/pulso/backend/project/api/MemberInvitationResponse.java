package com.pulso.backend.project.api;

import java.util.UUID;

import com.pulso.backend.project.application.CreatedInvitationResult;
import com.pulso.backend.project.domain.MemberInvitation;

public record MemberInvitationResponse(UUID id, UUID projectId, String inviteeEmail,
        UUID profileId, String role, String membershipStatus, UUID createdByProfileId) {
    public static MemberInvitationResponse from(CreatedInvitationResult result) {
        return from(result.id(), result.invitation());
    }
    public static MemberInvitationResponse from(UUID id, MemberInvitation invitation) {
        return new MemberInvitationResponse(id, invitation.projectId(), invitation.inviteeEmail(),
                invitation.profileId(), invitation.role(), invitation.membershipStatus(),
                invitation.createdByProfileId());
    }
}
