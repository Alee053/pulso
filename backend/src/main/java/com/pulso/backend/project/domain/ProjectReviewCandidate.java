package com.pulso.backend.project.domain;

import java.time.Instant;
import java.util.UUID;

public record ProjectReviewCandidate(
        UUID id,
        String name,
        String tagline,
        String description,
        String valueProposition,
        String sector,
        boolean sectorActive,
        String stage,
        String city,
        String department,
        String status,
        String verificationStatus,
        String creatorName,
        int activeOwnerCount,
        boolean canReview,
        Instant createdAt,
        Instant publishedAt) {
}
