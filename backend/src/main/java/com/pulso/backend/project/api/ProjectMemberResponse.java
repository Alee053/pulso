package com.pulso.backend.project.api;

import java.util.UUID;

import com.pulso.backend.project.domain.ProjectMember;

public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID profileId,
        String role,
        String membershipStatus,
        UUID createdByProfileId) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.id(),
                member.projectId(),
                member.profileId(),
                member.role(),
                member.membershipStatus(),
                member.createdByProfileId());
    }
}
