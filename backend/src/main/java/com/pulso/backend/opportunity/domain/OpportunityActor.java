package com.pulso.backend.opportunity.domain;

import java.util.UUID;

public record OpportunityActor(
        UUID projectId,
        UUID profileId,
        String projectStatus,
        UUID sectorId,
        String role,
        String membershipStatus) {

    public boolean canManage() {
        return "ACTIVE".equals(membershipStatus)
                && ("OWNER".equals(role) || "ADMIN".equals(role));
    }
}
