package com.pulso.backend.opportunity.api;

public record AssociateOpportunitySkillRequest(
        String skillId,
        String importance) {
}
