package com.pulso.backend.opportunity.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

public interface ProjectOpportunityRepository {

    Optional<OpportunityActor> lockActor(UUID projectId, UUID profileId);

    boolean lockActiveSector(UUID sectorId);

    List<ProjectOpportunity> list(UUID projectId, int offset, int limit);

    Optional<ProjectOpportunity> find(UUID projectId, UUID opportunityId);

    Optional<ProjectOpportunity> create(
            UUID projectId,
            UUID profileId,
            OpportunityContent content,
            String status);

    Optional<ProjectOpportunity> updateContent(
            UUID projectId,
            UUID opportunityId,
            UUID profileId,
            OffsetDateTime expectedUpdatedAt,
            OpportunityContent content);

    Optional<ProjectOpportunity> updateStatus(
            UUID projectId,
            UUID opportunityId,
            UUID profileId,
            String expectedStatus,
            OffsetDateTime expectedUpdatedAt,
            String nextStatus,
            OffsetDateTime closedAt);
}
