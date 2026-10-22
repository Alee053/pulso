package com.pulso.backend.opportunity.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.opportunity.domain.OpportunitySkill;

public interface OpportunitySkillRepository {

    List<OpportunitySkill> list(UUID projectId, UUID opportunityId);

    boolean isActiveSkill(UUID skillId);

    Optional<OpportunitySkill> create(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            String importance,
            UUID profileId);

    Optional<OpportunitySkill> updateImportance(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            String importance,
            UUID profileId);

    boolean delete(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            UUID profileId);
}
