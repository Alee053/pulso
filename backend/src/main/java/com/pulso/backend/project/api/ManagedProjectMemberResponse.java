package com.pulso.backend.project.api;

import java.util.UUID;

import com.pulso.backend.project.domain.ManagedProjectMember;

public record ManagedProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID profileId,
        String displayName,
        String inviteeEmail,
        String role,
        String membershipStatus) {

    public static ManagedProjectMemberResponse from(ManagedProjectMember member) {
        return new ManagedProjectMemberResponse(
                member.id(),
                member.projectId(),
                member.profileId(),
                member.displayName(),
                member.inviteeEmail(),
                member.role(),
                member.membershipStatus());
    }
}
