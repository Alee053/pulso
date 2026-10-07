package com.pulso.backend.project.domain;

import java.util.UUID;

public record ManagedProjectMember(
        UUID id,
        UUID projectId,
        UUID profileId,
        String displayName,
        String inviteeEmail,
        String role,
        String membershipStatus) {
}
