package com.pulso.backend.project.domain;

import java.util.UUID;

public record ProjectMember(
        UUID id,
        UUID projectId,
        UUID profileId,
        String role,
        String membershipStatus,
        UUID createdByProfileId) {

    public ProjectMember withRoleAndStatus(String role, String membershipStatus) {
        return new ProjectMember(
                id,
                projectId,
                profileId,
                role,
                membershipStatus,
                createdByProfileId);
    }
}
