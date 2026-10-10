package com.pulso.backend.opportunity.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ProjectOpportunityProperties;
import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityConflictException;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.OpportunityLifecycle;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;
import com.pulso.backend.opportunity.domain.OpportunityValidationException;

@Service
public class ManageProjectOpportunities {

    private final ProjectOpportunityRepository repository;
    private final ProjectOpportunityProperties properties;
    private final Clock clock;

    @Autowired
    public ManageProjectOpportunities(
            ProjectOpportunityRepository repository,
            ProjectOpportunityProperties properties) {
        this(repository, properties, Clock.systemUTC());
    }

    public ManageProjectOpportunities(
            ProjectOpportunityRepository repository,
            ProjectOpportunityProperties properties,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ProjectOpportunityPage list(UUID projectId, UUID authenticatedProfileId, int offset, int limit) {
        validatePaging(offset, limit);
        loadManager(projectId, authenticatedProfileId);
        List<ProjectOpportunity> rows = repository.list(projectId, offset, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ProjectOpportunity> data = hasMore ? rows.subList(0, limit) : rows;
        return new ProjectOpportunityPage(List.copyOf(data), offset, limit, hasMore);
    }

    @Transactional
    public ProjectOpportunity create(
            UUID projectId,
            UUID authenticatedProfileId,
            String opportunityType,
            String title,
            String description,
            String city,
            String department,
            Boolean remoteAllowed,
            String requestedStatus) {
        OpportunityActor actor = loadManager(projectId, authenticatedProfileId);
        OpportunityContent content = OpportunityContent.validate(
                opportunityType, title, description, city, department, remoteAllowed);
        String status = OpportunityLifecycle.initialStatus(requestedStatus);
        if ("OPEN".equals(status)) {
            requirePublicProject(actor);
        }
        return repository.create(projectId, authenticatedProfileId, content, status)
                .orElseThrow(ProjectOpportunityPermissionException::new);
    }

    @Transactional
    public ProjectOpportunity edit(
            UUID projectId,
            UUID opportunityId,
            UUID authenticatedProfileId,
            String opportunityType,
            String title,
            String description,
            String city,
            String department,
            Boolean remoteAllowed) {
        loadManager(projectId, authenticatedProfileId);
        OpportunityContent content = OpportunityContent.validate(
                opportunityType, title, description, city, department, remoteAllowed);
        ProjectOpportunity current = repository.find(projectId, opportunityId)
                .orElseThrow(ProjectOpportunityNotFoundException::new);
        if ("ARCHIVED".equals(current.status())) {
            throw new OpportunityConflictException("cannot edit an archived opportunity.");
        }
        return repository.updateContent(
                        projectId,
                        opportunityId,
                        authenticatedProfileId,
                        current.updatedAt(),
                        content)
                .orElseThrow(ProjectOpportunityConflictException::new);
    }

    @Transactional
    public ProjectOpportunity changeStatus(
            UUID projectId,
            UUID opportunityId,
            UUID authenticatedProfileId,
            String action) {
        OpportunityActor actor = loadManager(projectId, authenticatedProfileId);
        ProjectOpportunity current = repository.find(projectId, opportunityId)
                .orElseThrow(ProjectOpportunityNotFoundException::new);
        String nextStatus = OpportunityLifecycle.transition(current.status(), action);
        if ("OPEN".equals(nextStatus)) {
            requirePublicProject(actor);
        }
        OffsetDateTime closedAt = "CLOSED".equals(nextStatus)
                ? OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
                : null;
        return repository.updateStatus(
                        projectId,
                        opportunityId,
                        authenticatedProfileId,
                        current.status(),
                        current.updatedAt(),
                        nextStatus,
                        closedAt)
                .orElseThrow(ProjectOpportunityConflictException::new);
    }

    private OpportunityActor loadManager(UUID projectId, UUID authenticatedProfileId) {
        if (projectId == null || authenticatedProfileId == null) {
            throw new ProjectOpportunityPermissionException();
        }
        OpportunityActor actor = repository.lockActor(projectId, authenticatedProfileId)
                .filter(value -> projectId.equals(value.projectId()))
                .filter(value -> authenticatedProfileId.equals(value.profileId()))
                .filter(OpportunityActor::canManage)
                .orElseThrow(ProjectOpportunityPermissionException::new);
        return actor;
    }

    private void requirePublicProject(OpportunityActor actor) {
        if (!"PUBLISHED".equals(actor.projectStatus())
                || !repository.lockActiveSector(actor.sectorId())) {
            throw new OpportunityValidationException(
                    "OPEN opportunities require a published project with an active sector.");
        }
    }

    private void validatePaging(int offset, int limit) {
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new OpportunityValidationException(
                    "offset must be between 0 and " + properties.maxOffset() + ".");
        }
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new OpportunityValidationException(
                    "limit must be between 1 and " + properties.maxLimit() + ".");
        }
    }
}
