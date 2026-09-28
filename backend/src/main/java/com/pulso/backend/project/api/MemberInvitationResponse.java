package com.pulso.backend.project.api;

import java.util.UUID;

import com.pulso.backend.project.application.PersistedMemberInvitation;
import com.pulso.backend.project.domain.MemberInvitation;

public record MemberInvitationResponse(
        UUID id,
        UUID projectId,
        String inviteeEmail,
        UUID profileId,
        String role,
        String membershipStatus,
        UUID createdByProfileId) {

    public static MemberInvitationResponse from(PersistedMemberInvitation persisted) {
        MemberInvitation invitation = persisted.invitation();
        return new MemberInvitationResponse(
                persisted.id(),
                invitation.projectId(),
                invitation.inviteeEmail(),
                invitation.profileId(),
                invitation.role(),
                invitation.membershipStatus(),
                invitation.createdByProfileId());
    }
}
