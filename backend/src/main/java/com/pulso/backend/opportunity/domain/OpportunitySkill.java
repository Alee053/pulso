package com.pulso.backend.opportunity.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OpportunitySkill(
        UUID opportunityId,
        UUID skillId,
        String skillSlug,
        String skillName,
        String importance,
        boolean active,
        OffsetDateTime createdAt) {
}
