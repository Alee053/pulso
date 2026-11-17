package com.pulso.backend.project.domain;

import java.util.UUID;
import java.time.Instant;

public record CurrentProject(UUID projectId, String name, String role, String membershipStatus,
        String status, String verificationStatus, String reviewNote, Instant reviewedAt) {
}
