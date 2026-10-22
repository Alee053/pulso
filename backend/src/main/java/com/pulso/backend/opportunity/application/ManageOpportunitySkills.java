package com.pulso.backend.opportunity.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.opportunity.domain.OpportunitySkill;
import com.pulso.backend.opportunity.domain.OpportunitySkillImportance;
import com.pulso.backend.opportunity.domain.OpportunityValidationException;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

@Service
public class ManageOpportunitySkills {

    private final ProjectOpportunityRepository opportunityRepository;
    private final OpportunitySkillRepository skillRepository;

    public ManageOpportunitySkills(
            ProjectOpportunityRepository opportunityRepository,
            OpportunitySkillRepository skillRepository) {
        this.opportunityRepository = opportunityRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public List<OpportunitySkill> list(
            UUID projectId,
            UUID opportunityId,
            UUID authenticatedProfileId) {
        requireManagedOpportunity(projectId, opportunityId, authenticatedProfileId);
        return List.copyOf(skillRepository.list(projectId, opportunityId));
    }

    @Transactional
    public OpportunitySkill associate(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            String importance,
            UUID authenticatedProfileId) {
        requireManagedOpportunity(projectId, opportunityId, authenticatedProfileId);
        if (skillId == null) {
            throw new OpportunityValidationException("skillId is required.");
        }
        String normalizedImportance = OpportunitySkillImportance.parse(importance).name();
        if (!skillRepository.isActiveSkill(skillId)) {
            throw new OpportunityValidationException("skillId must reference an active skill.");
        }
        return skillRepository.create(
                        projectId, opportunityId, skillId, normalizedImportance, authenticatedProfileId)
                .orElseGet(() -> {
                    if (!skillRepository.isActiveSkill(skillId)) {
                        throw new OpportunityValidationException(
                                "skillId must reference an active skill.");
                    }
                    throw new OpportunitySkillConflictException();
                });
    }

    @Transactional
    public OpportunitySkill changeImportance(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            String importance,
            UUID authenticatedProfileId) {
        requireManagedOpportunity(projectId, opportunityId, authenticatedProfileId);
        if (skillId == null) {
            throw new OpportunityValidationException("skillId is required.");
        }
        String normalizedImportance = OpportunitySkillImportance.parse(importance).name();
        return skillRepository.updateImportance(
                        projectId, opportunityId, skillId, normalizedImportance, authenticatedProfileId)
                .orElseThrow(OpportunitySkillNotFoundException::new);
    }

    @Transactional
    public void remove(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            UUID authenticatedProfileId) {
        requireManagedOpportunity(projectId, opportunityId, authenticatedProfileId);
        if (skillId == null) {
            throw new OpportunitySkillNotFoundException();
        }
        if (!skillRepository.delete(projectId, opportunityId, skillId, authenticatedProfileId)) {
            throw new OpportunitySkillNotFoundException();
        }
    }

    private ProjectOpportunity requireManagedOpportunity(
            UUID projectId,
            UUID opportunityId,
            UUID authenticatedProfileId) {
        if (projectId == null || opportunityId == null || authenticatedProfileId == null) {
            throw new ProjectOpportunityPermissionException();
        }
        opportunityRepository.lockActor(projectId, authenticatedProfileId)
                .filter(actor -> actor.canManage()
                        && projectId.equals(actor.projectId())
                        && authenticatedProfileId.equals(actor.profileId()))
                .orElseThrow(ProjectOpportunityPermissionException::new);
        return opportunityRepository.find(projectId, opportunityId)
                .orElseThrow(ProjectOpportunityNotFoundException::new);
    }
}
