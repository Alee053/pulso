package com.pulso.backend.project.domain;

import java.util.UUID;
import java.time.Instant;

public record CurrentProject(
        UUID projectId,
        String name,
        String tagline,
        String description,
        String valueProposition,
        UUID sectorId,
        String sectorName,
        String stage,
        String city,
        String department,
        String role,
        String membershipStatus,
        String status,
        String verificationStatus,
        String reviewNote,
        Instant reviewedAt) {
}
