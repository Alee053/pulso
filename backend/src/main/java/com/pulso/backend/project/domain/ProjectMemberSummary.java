package com.pulso.backend.project.domain;

import java.util.UUID;

public record ProjectMemberSummary(
        UUID id,
        UUID projectId,
        UUID profileId,
        String role,
        String membershipStatus,
        String displayName,
        String displayTitle) {
}
