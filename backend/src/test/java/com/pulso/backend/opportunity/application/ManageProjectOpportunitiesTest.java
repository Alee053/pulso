package com.pulso.backend.opportunity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.config.ProjectOpportunityProperties;
import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.OpportunityConflictException;
import com.pulso.backend.opportunity.domain.OpportunityValidationException;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

class ManageProjectOpportunitiesTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OPPORTUNITY_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID SECTOR_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-01T12:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-10-02T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsForAnActiveOwnerAndUsesTheAuthenticatedProfileAsCreator() {
        FakeRepository repository = repository("OWNER", "ACTIVE", "PUBLISHED", true);
        ManageProjectOpportunities service = service(repository);

        ProjectOpportunity created = service.create(
                PROJECT_ID, PROFILE_ID, " mentor ", "  Mentoring  ", "  Support founders  ",
                " La Paz ", null, false, null);

        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(created.opportunityType()).isEqualTo("MENTOR");
        assertThat(created.title()).isEqualTo("Mentoring");
        assertThat(created.description()).isEqualTo("Support founders");
        assertThat(created.city()).isEqualTo("La Paz");
        assertThat(created.createdByProfileId()).isEqualTo(PROFILE_ID);
        assertThat(created.projectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void createsDraftsForExistingProjectsBeforeTheyArePublic() {
        FakeRepository repository = repository("ADMIN", "ACTIVE", "DRAFT", false);

        ProjectOpportunity created = service(repository).create(
                PROJECT_ID, PROFILE_ID, "TALENT", "Remote research", "Help with interviews",
                null, null, true, "DRAFT");

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.remoteAllowed()).isTrue();
    }

    @Test
    void rejectsMemberInactiveAndExternalActorsBeforeWriting() {
        for (OpportunityActor actor : List.of(
                actor("MEMBER", "ACTIVE", "PUBLISHED", SECTOR_ID),
                actor("OWNER", "LEFT", "PUBLISHED", SECTOR_ID),
                actor("ADMIN", "INVITED", "PUBLISHED", SECTOR_ID))) {
            FakeRepository repository = new FakeRepository(actor);
            assertThatThrownBy(() -> service(repository).create(
                    PROJECT_ID, PROFILE_ID, "TALENT", "Role", "Details", "La Paz", null, false, "DRAFT"))
                    .isInstanceOf(ProjectOpportunityPermissionException.class);
            assertThat(repository.created).isNull();
        }

        FakeRepository external = new FakeRepository(null);
        assertThatThrownBy(() -> service(external).create(
                PROJECT_ID, PROFILE_ID, "TALENT", "Role", "Details", "La Paz", null, false, "DRAFT"))
                .isInstanceOf(ProjectOpportunityPermissionException.class);
        assertThat(external.created).isNull();
    }

    @Test
    void rejectsAnOpenOpportunityUnlessItsProjectIsPublicAndItsSectorIsActive() {
        FakeRepository draftProject = repository("OWNER", "ACTIVE", "DRAFT", true);
        assertThatThrownBy(() -> service(draftProject).create(
                PROJECT_ID, PROFILE_ID, "TALENT", "Role", "Details", "La Paz", null, false, "OPEN"))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("OPEN opportunities require a published project with an active sector.");
        assertThat(draftProject.created).isNull();

        FakeRepository inactiveSector = repository("OWNER", "ACTIVE", "PUBLISHED", false);
        assertThatThrownBy(() -> service(inactiveSector).create(
                PROJECT_ID, PROFILE_ID, "TALENT", "Role", "Details", "La Paz", null, false, "OPEN"))
                .isInstanceOf(OpportunityValidationException.class);
        assertThat(inactiveSector.created).isNull();
    }

    @Test
    void editsOnlyContentAndPreservesIdentityCreatorProjectStatusAndCreationTime() {
        FakeRepository repository = repository("ADMIN", "ACTIVE", "PUBLISHED", true);
        repository.opportunities.add(opportunity("PAUSED"));

        ProjectOpportunity updated = service(repository).edit(
                PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "CUSTOMER", "Updated title",
                "Updated details", "Santa Cruz", "Santa Cruz", true);

        assertThat(updated.id()).isEqualTo(OPPORTUNITY_ID);
        assertThat(updated.projectId()).isEqualTo(PROJECT_ID);
        assertThat(updated.createdByProfileId()).isEqualTo(PROFILE_ID);
        assertThat(updated.status()).isEqualTo("PAUSED");
        assertThat(updated.createdAt()).isEqualTo(CREATED_AT);
        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.updatedAt()).isAfter(UPDATED_AT);
    }

    @Test
    void doesNotEditArchivedOrCrossProjectOpportunities() {
        FakeRepository archived = repository("OWNER", "ACTIVE", "PUBLISHED", true);
        archived.opportunities.add(opportunity("ARCHIVED"));
        assertThatThrownBy(() -> service(archived).edit(
                PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "TALENT", "Title", "Details",
                "La Paz", null, false))
                .isInstanceOf(OpportunityConflictException.class)
                .hasMessage("cannot edit an archived opportunity.");

        FakeRepository wrongProject = repository("OWNER", "ACTIVE", "PUBLISHED", true);
        wrongProject.opportunities.add(opportunity("DRAFT"));
        assertThatThrownBy(() -> service(wrongProject).edit(
                OTHER_PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "TALENT", "Title", "Details",
                "La Paz", null, false))
                .isInstanceOf(ProjectOpportunityPermissionException.class);

        FakeRepository isolated = repository("OWNER", "ACTIVE", "PUBLISHED", true);
        isolated.opportunities.add(opportunity(OTHER_PROJECT_ID, "DRAFT"));
        assertThatThrownBy(() -> service(isolated).edit(
                PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "TALENT", "Title", "Details",
                "La Paz", null, false))
                .isInstanceOf(ProjectOpportunityNotFoundException.class);
    }

    @Test
    void rejectsEditsAndStateChangesByNonManagers() {
        for (OpportunityActor actor : List.of(
                actor("MEMBER", "ACTIVE", "PUBLISHED", SECTOR_ID),
                actor("OWNER", "LEFT", "PUBLISHED", SECTOR_ID),
                actor("ADMIN", "INVITED", "PUBLISHED", SECTOR_ID))) {
            FakeRepository repository = new FakeRepository(actor);
            repository.opportunities.add(opportunity("OPEN"));
            ManageProjectOpportunities service = service(repository);

            assertThatThrownBy(() -> service.edit(
                    PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "TALENT", "Changed", "Details",
                    "La Paz", null, false))
                    .isInstanceOf(ProjectOpportunityPermissionException.class);
            assertThatThrownBy(() -> service.changeStatus(
                    PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "PAUSE"))
                    .isInstanceOf(ProjectOpportunityPermissionException.class);
            assertThat(repository.opportunities.getFirst().status()).isEqualTo("OPEN");
            assertThat(repository.opportunities.getFirst().title()).isEqualTo("Original title");
        }

        FakeRepository external = new FakeRepository(null);
        external.opportunities.add(opportunity("OPEN"));
        assertThatThrownBy(() -> service(external).changeStatus(
                PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "PAUSE"))
                .isInstanceOf(ProjectOpportunityPermissionException.class);
    }

    @Test
    void appliesOnlySupportedTransitionsAndSetsClosedAtFromTheServerClock() {
        FakeRepository repository = repository("OWNER", "ACTIVE", "PUBLISHED", true);
        repository.opportunities.add(opportunity("DRAFT"));
        ManageProjectOpportunities service = service(repository);

        assertThat(service.changeStatus(PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "OPEN").status())
                .isEqualTo("OPEN");
        assertThat(service.changeStatus(PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "PAUSE").status())
                .isEqualTo("PAUSED");
        assertThat(service.changeStatus(PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "OPEN").status())
                .isEqualTo("OPEN");
        ProjectOpportunity closed = service.changeStatus(
                PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "CLOSE");
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isEqualTo(OffsetDateTime.parse("2026-10-10T12:00Z"));
        assertThat(service.changeStatus(PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "ARCHIVE").status())
                .isEqualTo("ARCHIVED");
    }

    @Test
    void rejectsInvalidTransitionsAndDetectsConcurrentUpdates() {
        FakeRepository repository = repository("OWNER", "ACTIVE", "PUBLISHED", true);
        repository.opportunities.add(opportunity("CLOSED"));
        ManageProjectOpportunities service = service(repository);
        assertThatThrownBy(() -> service.changeStatus(
                PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "OPEN"))
                .isInstanceOf(OpportunityConflictException.class);

        repository.opportunities.set(0, opportunity("OPEN"));
        repository.forceConflict = true;
        assertThatThrownBy(() -> service.changeStatus(
                PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID, "PAUSE"))
                .isInstanceOf(ProjectOpportunityConflictException.class);
    }

    @Test
    void returnsBoundedPagesAndRejectsInvalidPagination() {
        FakeRepository repository = repository("OWNER", "ACTIVE", "PUBLISHED", true);
        repository.opportunities.add(opportunity("DRAFT"));

        ProjectOpportunityPage page = service(repository).list(PROJECT_ID, PROFILE_ID, 0, 1);
        assertThat(page.data()).hasSize(1);
        assertThat(page.offset()).isZero();
        assertThat(page.limit()).isEqualTo(1);
        assertThat(page.hasMore()).isFalse();
        assertThatThrownBy(() -> service(repository).list(PROJECT_ID, PROFILE_ID, 0, 51))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("limit must be between 1 and 50.");
    }

    private static ManageProjectOpportunities service(FakeRepository repository) {
        return new ManageProjectOpportunities(
                repository, new ProjectOpportunityProperties(0, 20, 50, 100_000), CLOCK);
    }

    private static FakeRepository repository(
            String role, String membershipStatus, String projectStatus, boolean activeSector) {
        FakeRepository repository = new FakeRepository(
                actor(role, membershipStatus, projectStatus, SECTOR_ID));
        repository.activeSector = activeSector;
        return repository;
    }

    private static OpportunityActor actor(
            String role, String membershipStatus, String projectStatus, UUID sectorId) {
        return new OpportunityActor(PROJECT_ID, PROFILE_ID, projectStatus, sectorId, role, membershipStatus);
    }

    private static ProjectOpportunity opportunity(String status) {
        return opportunity(PROJECT_ID, status);
    }

    private static ProjectOpportunity opportunity(UUID projectId, String status) {
        return new ProjectOpportunity(
                OPPORTUNITY_ID, projectId, "TALENT", "Original title", "Original details", status,
                "La Paz", null, false, PROFILE_ID, null, null, null, CREATED_AT, UPDATED_AT);
    }

    private static final class FakeRepository implements ProjectOpportunityRepository {

        private OpportunityActor actor;
        private boolean activeSector = true;
        private boolean forceConflict;
        private OpportunityContent created;
        private String createdStatus;
        private final List<ProjectOpportunity> opportunities = new ArrayList<>();

        private FakeRepository(OpportunityActor actor) {
            this.actor = actor;
        }

        @Override
        public Optional<OpportunityActor> lockActor(UUID projectId, UUID profileId) {
            if (actor == null || !actor.projectId().equals(projectId)
                    || !actor.profileId().equals(profileId)) {
                return Optional.empty();
            }
            return Optional.of(actor);
        }

        @Override
        public boolean lockActiveSector(UUID sectorId) {
            return activeSector && SECTOR_ID.equals(sectorId);
        }

        @Override
        public List<ProjectOpportunity> list(UUID projectId, int offset, int limit) {
            return opportunities.stream()
                    .filter(opportunity -> opportunity.projectId().equals(projectId))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<ProjectOpportunity> find(UUID projectId, UUID opportunityId) {
            return opportunities.stream()
                    .filter(opportunity -> opportunity.projectId().equals(projectId))
                    .filter(opportunity -> opportunity.id().equals(opportunityId))
                    .findFirst();
        }

        @Override
        public Optional<ProjectOpportunity> create(
                UUID projectId,
                UUID profileId,
                OpportunityContent content,
                String status) {
            created = content;
            createdStatus = status;
            ProjectOpportunity createdOpportunity = new ProjectOpportunity(
                    OPPORTUNITY_ID, projectId, content.opportunityType(), content.title(), content.description(),
                    status, content.city(), content.department(), content.remoteAllowed(), profileId,
                    null, null, null, CREATED_AT, UPDATED_AT);
            opportunities.add(createdOpportunity);
            return Optional.of(createdOpportunity);
        }

        @Override
        public Optional<ProjectOpportunity> updateContent(
                UUID projectId,
                UUID opportunityId,
                UUID profileId,
                OffsetDateTime expectedUpdatedAt,
                OpportunityContent content) {
            if (forceConflict) {
                return Optional.empty();
            }
            return find(projectId, opportunityId).map(current -> replace(
                    current,
                    content.opportunityType(),
                    content.title(),
                    content.description(),
                    content.city(),
                    content.department(),
                    content.remoteAllowed(),
                    current.status(),
                    current.closedAt()));
        }

        @Override
        public Optional<ProjectOpportunity> updateStatus(
                UUID projectId,
                UUID opportunityId,
                UUID profileId,
                String expectedStatus,
                OffsetDateTime expectedUpdatedAt,
                String nextStatus,
                OffsetDateTime closedAt) {
            if (forceConflict) {
                return Optional.empty();
            }
            return find(projectId, opportunityId).map(current -> replace(
                    current,
                    current.opportunityType(),
                    current.title(),
                    current.description(),
                    current.city(),
                    current.department(),
                    current.remoteAllowed(),
                    nextStatus,
                    closedAt == null ? current.closedAt() : closedAt));
        }

        private ProjectOpportunity replace(
                ProjectOpportunity current,
                String type,
                String title,
                String description,
                String city,
                String department,
                boolean remoteAllowed,
                String status,
                OffsetDateTime closedAt) {
            ProjectOpportunity updated = new ProjectOpportunity(
                    current.id(), current.projectId(), type, title, description, status, city, department,
                    remoteAllowed, current.createdByProfileId(), current.startsAt(), current.endsAt(),
                    closedAt, current.createdAt(), UPDATED_AT.plusSeconds(1));
            opportunities.replaceAll(value -> value.id().equals(updated.id()) ? updated : value);
            return updated;
        }
    }
}
