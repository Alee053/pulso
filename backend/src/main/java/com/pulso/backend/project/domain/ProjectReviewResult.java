package com.pulso.backend.project.domain;

import java.time.Instant;
import java.util.UUID;

public record ProjectReviewResult(
        UUID projectId,
        String status,
        String verificationStatus,
        String reviewNote,
        UUID reviewedByProfileId,
        Instant reviewedAt,
        Instant publishedAt) {
}
