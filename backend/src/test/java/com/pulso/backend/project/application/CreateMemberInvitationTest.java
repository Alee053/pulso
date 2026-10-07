package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.DuplicateMemberInvitationException;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;
import com.pulso.backend.project.domain.MemberInvitationPermissionException;
import com.pulso.backend.project.domain.MemberInvitationProjectNotFoundException;

class CreateMemberInvitationTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INVITATION_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final ProjectProperties PROPERTIES =
            new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254);

    @Test
    void createsANormalizedInvitationAndPersistsOnlyTheDigest() {
        CapturingRepository repository = repository();
        CreateMemberInvitation service = service(repository);

        PersistedMemberInvitation result = service.execute(
                PROJECT_ID,
                OWNER_ID,
                " Ada@Example.COM ");

        assertThat(result.id()).isEqualTo(INVITATION_ID);
        assertThat(result.invitation().inviteeEmail()).isEqualTo("ada@example.com");
        assertThat(result.invitation().profileId()).isNull();
        assertThat(result.invitation().role()).isEqualTo("MEMBER");
        assertThat(result.invitation().membershipStatus()).isEqualTo("INVITED");
        assertThat(result.invitation().createdByProfileId()).isEqualTo(OWNER_ID);
        assertThat(repository.saved.invitation()).isEqualTo(result.invitation());
        assertThat(repository.saved.inviteTokenDigest())
                .isEqualTo("69a24810112e8f3e257dfd85e240bcc8e8a60155787eaa0ec9526f584f0deb7d");
        assertThat(repository.createCount).isEqualTo(1);
    }

    @Test
    void deniesAnActiveMemberWithoutPersisting() {
        CapturingRepository repository = repository();
        repository.actor = new MemberInvitationActor(PROJECT_ID, OWNER_ID, "MEMBER", "ACTIVE");

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID,
                OWNER_ID,
                "ada@example.com"))
                .isInstanceOf(MemberInvitationPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");

        assertThat(repository.createCount).isZero();
    }

    @Test
    void rejectsAnUnknownProjectBeforeReadingMemberships() {
        CapturingRepository repository = repository();
        repository.projectExists = false;

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID,
                OWNER_ID,
                "ada@example.com"))
                .isInstanceOf(MemberInvitationProjectNotFoundException.class)
                .hasMessage("project does not exist.");

        assertThat(repository.actorLookups).isZero();
        assertThat(repository.createCount).isZero();
    }

    @Test
    void rejectsAnActiveDuplicateBeforePersistence() {
        CapturingRepository repository = repository();
        repository.invitations = List.of(invited("ada@example.com"));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID,
                OWNER_ID,
                " ADA@example.com "))
                .isInstanceOf(DuplicateMemberInvitationException.class)
                .hasMessage("an active invitation already exists for this project and email.");

        assertThat(repository.createCount).isZero();
    }

    private static CreateMemberInvitation service(CapturingRepository repository) {
        return new CreateMemberInvitation(repository, () -> "accept-me", PROPERTIES, (email, token) -> { });
    }

    private static CapturingRepository repository() {
        CapturingRepository repository = new CapturingRepository();
        repository.actor = new MemberInvitationActor(PROJECT_ID, OWNER_ID, "OWNER", "ACTIVE");
        return repository;
    }

    private static MemberInvitation invited(String email) {
        return new MemberInvitation(
                PROJECT_ID,
                email,
                null,
                "MEMBER",
                "INVITED",
                OWNER_ID,
                new MemberInvitation.Limits(254));
    }

    private static final class CapturingRepository implements MemberInvitationRepository {

        private boolean projectExists = true;
        private MemberInvitationActor actor;
        private List<MemberInvitation> invitations = List.of();
        private CreatedMemberInvitation saved;
        private int actorLookups;
        private int createCount;

        @Override
        public boolean projectExists(UUID projectId) {
            return projectExists;
        }

        @Override
        public Optional<MemberInvitationActor> findActor(UUID projectId, UUID profileId) {
            actorLookups++;
            return Optional.ofNullable(actor);
        }

        @Override
        public List<MemberInvitation> findInvitations(UUID projectId) {
            return invitations;
        }

        @Override
        public PersistedMemberInvitation createInvitation(CreatedMemberInvitation invitation) {
            saved = invitation;
            createCount++;
            return new PersistedMemberInvitation(INVITATION_ID, invitation.invitation());
        }
    }
}
