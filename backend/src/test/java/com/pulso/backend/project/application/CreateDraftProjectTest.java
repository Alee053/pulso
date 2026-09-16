package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.project.domain.CreatedDraftProject;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectValidationException;

class CreateDraftProjectTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MEMBER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void createsADraftAndItsActiveOwnerForTheAuthenticatedProfile() {
        CapturingRepository repository = new CapturingRepository();

        CreatedDraftProject result = new CreateDraftProject(repository).execute(
                PROFILE_ID,
                draft());

        assertThat(repository.calls).containsExactly(
                "hasActiveSector",
                "createDraft",
                "createOwner");
        assertThat(repository.createdByProfileId).isEqualTo(PROFILE_ID);
        assertThat(repository.ownerProfileId).isEqualTo(PROFILE_ID);
        assertThat(result.project().status()).isEqualTo("DRAFT");
        assertThat(result.project().createdByProfileId()).isEqualTo(PROFILE_ID);
        assertThat(result.owner().role()).isEqualTo("OWNER");
        assertThat(result.owner().membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsAnInactiveOrUnknownSectorBeforeCreatingAnything() {
        CapturingRepository repository = new CapturingRepository();
        repository.activeSector = false;

        assertThatThrownBy(() -> new CreateDraftProject(repository).execute(
                PROFILE_ID,
                draft()))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("sectorId must reference an active sector.");

        assertThat(repository.calls).containsExactly("hasActiveSector");
    }

    @Test
    void propagatesOwnerFailureSoTheApplicationTransactionCanRollback() {
        CapturingRepository repository = new CapturingRepository();
        repository.ownerFailure = new IllegalStateException("owner insert failed");

        assertThatThrownBy(() -> new CreateDraftProject(repository).execute(
                PROFILE_ID,
                draft()))
                .isSameAs(repository.ownerFailure);

        assertThat(repository.calls).containsExactly(
                "hasActiveSector",
                "createDraft",
                "createOwner");
    }

    @Test
    void propagatesProjectPersistenceConflictWithoutCreatingAnOwner() {
        CapturingRepository repository = new CapturingRepository();
        repository.draftFailure = new ProjectConflictException();

        assertThatThrownBy(() -> new CreateDraftProject(repository).execute(
                PROFILE_ID,
                draft()))
                .isSameAs(repository.draftFailure);

        assertThat(repository.calls).containsExactly(
                "hasActiveSector",
                "createDraft");
    }

    @Test
    void rejectsMissingAuthenticatedProfile() {
        assertThatThrownBy(() -> new CreateDraftProject(new CapturingRepository()).execute(
                null,
                draft()))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("authenticatedProfileId is required.");
    }

    private static ProjectDraft draft() {
        return new ProjectDraft(
                "eco-pack",
                "EcoPack Bolivia",
                "Envases sostenibles",
                "Proyecto de prueba.",
                SECTOR_ID,
                "Cochabamba",
                "mvp");
    }

    private static final class CapturingRepository implements ProjectRepository {

        private final List<String> calls = new ArrayList<>();
        private boolean activeSector = true;
        private RuntimeException draftFailure;
        private RuntimeException ownerFailure;
        private UUID createdByProfileId;
        private UUID ownerProfileId;

        @Override
        public boolean hasActiveSector(UUID sectorId) {
            calls.add("hasActiveSector");
            return activeSector;
        }

        @Override
        public Project createDraft(ProjectDraft draft, UUID createdByProfileId) {
            calls.add("createDraft");
            this.createdByProfileId = createdByProfileId;
            if (draftFailure != null) {
                throw draftFailure;
            }
            return new Project(
                    PROJECT_ID,
                    draft.slug(),
                    draft.name(),
                    draft.tagline(),
                    draft.description(),
                    draft.sectorId(),
                    draft.city(),
                    draft.stage(),
                    "DRAFT",
                    createdByProfileId);
        }

        @Override
        public ProjectMember createOwner(UUID projectId, UUID profileId) {
            calls.add("createOwner");
            ownerProfileId = profileId;
            if (ownerFailure != null) {
                throw ownerFailure;
            }
            return new ProjectMember(
                    MEMBER_ID,
                    projectId,
                    profileId,
                    "OWNER",
                    "ACTIVE",
                    profileId);
        }
    }
}
