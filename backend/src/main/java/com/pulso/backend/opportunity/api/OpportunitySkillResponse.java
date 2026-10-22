package com.pulso.backend.opportunity.api;

import java.util.UUID;

import com.pulso.backend.opportunity.domain.OpportunitySkill;

public record OpportunitySkillResponse(
        UUID skillId,
        String slug,
        String name,
        String importance,
        boolean active) {

    public static OpportunitySkillResponse from(OpportunitySkill skill) {
        return new OpportunitySkillResponse(
                skill.skillId(),
                skill.skillSlug(),
                skill.skillName(),
                skill.importance(),
                skill.active());
    }
}
