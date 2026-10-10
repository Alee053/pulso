package com.pulso.backend.opportunity.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectOpportunity(
        UUID id,
        UUID projectId,
        String opportunityType,
        String title,
        String description,
        String status,
        String city,
        String department,
        boolean remoteAllowed,
        UUID createdByProfileId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
