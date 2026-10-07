package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.ProjectValidationException;
import com.pulso.backend.project.domain.UpdatedProject;

class UpdateProjectInformationTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SECTOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final ProjectProperties PROPERTIES = new ProjectProperties(
            2,
            120,
            160,
            240,
            5000,
            120,
            40,
            254);

    @Test
    void updatesTheProjectForTheAuthenticatedProfile() {
        CapturingRepository repository = new CapturingRepository();
        ProjectUpdatePatch patch = patch();

        UpdatedProject result = new UpdateProjectInformation(repository, PROPERTIES)
                .execute(PROJECT_ID, PROFILE_ID, patch);

        assertThat(repository.projectId).isEqualTo(PROJECT_ID);
        assertThat(repository.authenticatedProfileId).isEqualTo(PROFILE_ID);
        assertThat(repository.patch).isSameAs(patch);
        assertThat(repository.checkedSectorId).isEqualTo(SECTOR_ID);
        assertThat(result.name()).isEqualTo("EcoPack actualizado");
    }

    @Test
    void allowsPartialEditsWithoutLookingUpAnOmittedSector() {
        CapturingRepository repository = new CapturingRepository();
        repository.activeSector = false;
        ProjectUpdatePatch patch = new ProjectUpdatePatch(
                "Solo nombre", null, null, null, null, null, null, null,
                new ProjectUpdatePatch.Limits(160, 240, 5000, 120, 40));

        UpdatedProject result = new UpdateProjectInformation(repository, PROPERTIES)
                .execute(PROJECT_ID, PROFILE_ID, patch);

        assertThat(repository.checkedSectorId).isNull();
        assertThat(repository.patch).isSameAs(patch);
        assertThat(result).isEqualTo(project());
    }

    @Test
    void validatesTheSectorBeforeWriting() {
        CapturingRepository repository = new CapturingRepository();
        repository.activeSector = false;

        assertThatThrownBy(() -> new UpdateProjectInformation(repository, PROPERTIES)
                .execute(PROJECT_ID, PROFILE_ID, patch()))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("sectorId must reference an active sector.");

        assertThat(repository.patch).isNull();
        assertThat(repository.checkedSectorId).isEqualTo(SECTOR_ID);
    }

    @Test
    void mapsMissingProjectAndInsufficientMembershipToTheSameAuthorizationError() {
        CapturingRepository repository = new CapturingRepository();
        repository.result = Optional.empty();

        assertThatThrownBy(() -> new UpdateProjectInformation(repository, PROPERTIES)
                .execute(PROJECT_ID, PROFILE_ID, patch()))
                .isInstanceOf(ProjectUpdateAuthorizationException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
    }

    @Test
    void rejectsMissingTargetOrAuthenticatedIdentity() {
        UpdateProjectInformation useCase = new UpdateProjectInformation(
                new CapturingRepository(),
                PROPERTIES);

        assertThatThrownBy(() -> useCase.execute(null, PROFILE_ID, patch()))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("projectId is required.");
        assertThatThrownBy(() -> useCase.execute(PROJECT_ID, null, patch()))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("authenticatedProfileId is required.");
        assertThatThrownBy(() -> useCase.execute(PROJECT_ID, PROFILE_ID, null))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("project update is required.");
    }

    private static ProjectUpdatePatch patch() {
        return new ProjectUpdatePatch(
                "EcoPack actualizado",
                "Envases nuevos",
                "Descripción nueva",
                "Menos residuos",
                "MVP",
                SECTOR_ID,
                "Cochabamba",
                "Cochabamba",
                new ProjectUpdatePatch.Limits(160, 240, 5000, 120, 40));
    }

    private static UpdatedProject project() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-08T12:00:00Z");
        return new UpdatedProject(
                PROJECT_ID,
                "eco-pack",
                "EcoPack actualizado",
                "Envases nuevos",
                "Descripción nueva",
                "Menos residuos",
                SECTOR_ID,
                "MVP",
                "Cochabamba",
                "Cochabamba",
                "DRAFT",
                "UNVERIFIED",
                PROFILE_ID,
                null,
                null,
                null,
                null,
                timestamp,
                timestamp,
                null);
    }

    private static final class CapturingRepository implements ProjectUpdateRepository {

        private boolean activeSector = true;
        private Optional<UpdatedProject> result = Optional.of(project());
        private UUID projectId;
        private UUID authenticatedProfileId;
        private ProjectUpdatePatch patch;
        private UUID checkedSectorId;

        @Override
        public boolean hasActiveSector(UUID sectorId) {
            checkedSectorId = sectorId;
            return activeSector;
        }

        @Override
        public Optional<UpdatedProject> updateInformation(
                UUID projectId,
                UUID authenticatedProfileId,
                ProjectUpdatePatch patch) {
            this.projectId = projectId;
            this.authenticatedProfileId = authenticatedProfileId;
            this.patch = patch;
            return result;
        }
    }
}
