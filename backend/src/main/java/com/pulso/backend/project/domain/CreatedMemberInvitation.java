package com.pulso.backend.project.domain;

public record CreatedMemberInvitation(
        MemberInvitation invitation,
        String inviteTokenDigest) {

    public CreatedMemberInvitation {
        if (invitation == null) {
            throw new ProjectValidationException("invitation is required.");
        }
        inviteTokenDigest = InviteTokenDigest.require(inviteTokenDigest);
    }
}
