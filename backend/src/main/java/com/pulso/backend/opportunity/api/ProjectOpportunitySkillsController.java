package com.pulso.backend.opportunity.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.opportunity.application.ManageOpportunitySkills;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/opportunities/{opportunityId}/skills")
public class ProjectOpportunitySkillsController {

    private final ManageOpportunitySkills manageOpportunitySkills;

    public ProjectOpportunitySkillsController(ManageOpportunitySkills manageOpportunitySkills) {
        this.manageOpportunitySkills = manageOpportunitySkills;
    }

    @GetMapping
    public List<OpportunitySkillResponse> list(
            @PathVariable UUID projectId,
            @PathVariable UUID opportunityId,
            @AuthenticationPrincipal Jwt jwt) {
        return manageOpportunitySkills.list(
                        projectId, opportunityId, authenticatedProfileId(jwt))
                .stream()
                .map(OpportunitySkillResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpportunitySkillResponse associate(
            @PathVariable UUID projectId,
            @PathVariable UUID opportunityId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AssociateOpportunitySkillRequest request) {
        if (request == null) {
            throw new InvalidRequestException("The request body is required.");
        }
        UUID skillId;
        try {
            skillId = UUID.fromString(request.skillId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRequestException("skillId must be a valid UUID.");
        }
        return OpportunitySkillResponse.from(manageOpportunitySkills.associate(
                projectId, opportunityId, skillId, request.importance(), authenticatedProfileId(jwt)));
    }

    @PutMapping("/{skillId}")
    public OpportunitySkillResponse changeImportance(
            @PathVariable UUID projectId,
            @PathVariable UUID opportunityId,
            @PathVariable UUID skillId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateOpportunitySkillRequest request) {
        if (request == null) {
            throw new InvalidRequestException("The request body is required.");
        }
        return OpportunitySkillResponse.from(manageOpportunitySkills.changeImportance(
                projectId, opportunityId, skillId, request.importance(), authenticatedProfileId(jwt)));
    }

    @DeleteMapping("/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable UUID projectId,
            @PathVariable UUID opportunityId,
            @PathVariable UUID skillId,
            @AuthenticationPrincipal Jwt jwt) {
        manageOpportunitySkills.remove(
                projectId, opportunityId, skillId, authenticatedProfileId(jwt));
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidAuthenticatedSubjectException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }
}
