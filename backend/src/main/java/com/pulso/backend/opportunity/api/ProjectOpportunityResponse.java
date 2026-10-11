package com.pulso.backend.opportunity.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.pulso.backend.opportunity.domain.ProjectOpportunity;

public record ProjectOpportunityResponse(
        UUID id,
        UUID projectId,
        String opportunityType,
        String title,
        String description,
        String status,
        String city,
        String department,
        boolean remoteAllowed,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProjectOpportunityResponse from(ProjectOpportunity opportunity) {
        return new ProjectOpportunityResponse(
                opportunity.id(),
                opportunity.projectId(),
                opportunity.opportunityType(),
                opportunity.title(),
                opportunity.description(),
                opportunity.status(),
                opportunity.city(),
                opportunity.department(),
                opportunity.remoteAllowed(),
                opportunity.createdAt(),
                opportunity.updatedAt());
    }
}
