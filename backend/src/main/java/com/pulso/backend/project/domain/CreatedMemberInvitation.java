package com.pulso.backend.project.domain;

import com.pulso.backend.config.ApiMessages;

public record CreatedMemberInvitation(
        MemberInvitation invitation,
        String inviteTokenDigest) {

    public CreatedMemberInvitation {
        if (invitation == null) {
            throw new ProjectValidationException(ApiMessages.INVITATION_REQUIRED);
        }
        inviteTokenDigest = InviteTokenDigest.require(inviteTokenDigest);
    }
}
