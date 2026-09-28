package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.project.domain.InviteTokenDigest;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.ProjectValidationException;

class AcceptMemberInvitationTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID CREATOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCEPTING_PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID INVITATION_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final String INVITEE_EMAIL = "ada@example.com";
    private static final String PLAINTEXT_TOKEN = "accept-me";
    private static final String TOKEN_DIGEST = InviteTokenDigest.fromPlaintext(PLAINTEXT_TOKEN);
    private static final MemberInvitation.Limits LIMITS =
            new MemberInvitation.Limits(254);

    @Test
    void acceptsTheInvitationUsingTheDigestAndAuthenticatedEmail() {
        CapturingRepository repository = repository();

        PersistedMemberInvitation accepted = service(repository).execute(
                ACCEPTING_PROFILE_ID,
                " Ada@Example.COM ",
                PLAINTEXT_TOKEN);

        assertThat(accepted.id()).isEqualTo(INVITATION_ID);
        assertThat(accepted.invitation().profileId()).isEqualTo(ACCEPTING_PROFILE_ID);
        assertThat(accepted.invitation().membershipStatus()).isEqualTo("ACTIVE");
        assertThat(accepted.invitation().role()).isEqualTo("MEMBER");
        assertThat(repository.lookupDigest).isEqualTo(TOKEN_DIGEST);
        assertThat(repository.acceptedDigest).isEqualTo(TOKEN_DIGEST);
        assertThat(repository.acceptedProfileId).isEqualTo(ACCEPTING_PROFILE_ID);
        assertThat(accepted.toString()).doesNotContain(PLAINTEXT_TOKEN);
    }

    @Test
    void rejectsAnInvalidOrMissingInvitationWithoutUpdatingTheMembership() {
        CapturingRepository repository = repository();

        assertThatThrownBy(() -> service(repository).execute(
                ACCEPTING_PROFILE_ID,
                INVITEE_EMAIL,
                "wrong-token"))
                .isInstanceOf(MemberInvitationNotFoundException.class)
                .hasMessage("invitation not found or already used.");

        assertThat(repository.acceptCalls).isZero();
        assertThat(repository.pending.invitation().membershipStatus()).isEqualTo("INVITED");
        assertThat(repository.pending.invitation().profileId()).isNull();
    }

    @Test
    void rejectsAEmailMismatchBeforeAssociatingTheProfile() {
        CapturingRepository repository = repository();

        assertThatThrownBy(() -> service(repository).execute(
                ACCEPTING_PROFILE_ID,
                "other@example.com",
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertThat(repository.acceptCalls).isZero();
        assertThat(repository.pending.invitation().membershipStatus()).isEqualTo("INVITED");
        assertThat(repository.pending.invitation().profileId()).isNull();
    }

    @Test
    void rejectsAnAlreadyAcceptedInvitationWithoutChangingIt() {
        CapturingRepository repository = repository();
        repository.pending = new PersistedMemberInvitation(
                INVITATION_ID,
                new MemberInvitation(
                        PROJECT_ID,
                        INVITEE_EMAIL,
                        ACCEPTING_PROFILE_ID,
                        "MEMBER",
                        "ACTIVE",
                        CREATOR_ID,
                        LIMITS));

        assertThatThrownBy(() -> service(repository).execute(
                ACCEPTING_PROFILE_ID,
                INVITEE_EMAIL,
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertThat(repository.acceptCalls).isZero();
        assertThat(repository.pending.invitation().profileId()).isEqualTo(ACCEPTING_PROFILE_ID);
        assertThat(repository.pending.invitation().membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsAProfileThatAlreadyHasAProjectMembership() {
        CapturingRepository repository = repository();
        repository.membershipExists = true;

        assertThatThrownBy(() -> service(repository).execute(
                ACCEPTING_PROFILE_ID,
                INVITEE_EMAIL,
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertThat(repository.acceptCalls).isZero();
        assertThat(repository.pending.invitation().profileId()).isNull();
        assertThat(repository.pending.invitation().membershipStatus()).isEqualTo("INVITED");
    }

    @Test
    void consumesTheDigestSoAControlledRetryCannotReplayForAnotherProfile() {
        CapturingRepository repository = repository();
        AcceptMemberInvitation acceptance = service(repository);

        acceptance.execute(ACCEPTING_PROFILE_ID, INVITEE_EMAIL, PLAINTEXT_TOKEN);

        assertThatThrownBy(() -> acceptance.execute(
                OTHER_PROFILE_ID,
                INVITEE_EMAIL,
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertThat(repository.acceptCalls).isEqualTo(1);
        assertThat(repository.pending.invitation().profileId()).isEqualTo(ACCEPTING_PROFILE_ID);
        assertThat(repository.pending.invitation().membershipStatus()).isEqualTo("ACTIVE");
        assertThat(repository.lookupDigest).isEqualTo(TOKEN_DIGEST);
    }

    @Test
    void rejectsAControlledRetryByTheSameProfileWithoutChangingTheAcceptedMembership() {
        CapturingRepository repository = repository();
        AcceptMemberInvitation acceptance = service(repository);

        acceptance.execute(ACCEPTING_PROFILE_ID, INVITEE_EMAIL, PLAINTEXT_TOKEN);

        assertThatThrownBy(() -> acceptance.execute(
                ACCEPTING_PROFILE_ID,
                INVITEE_EMAIL,
                PLAINTEXT_TOKEN))
                .isInstanceOf(MemberInvitationNotFoundException.class);

        assertThat(repository.acceptCalls).isEqualTo(1);
        assertThat(repository.pending.invitation().profileId()).isEqualTo(ACCEPTING_PROFILE_ID);
        assertThat(repository.pending.invitation().membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsARequestWithoutAnAuthenticatedProfileBeforeReadingTheToken() {
        CapturingRepository repository = repository();

        assertThatThrownBy(() -> service(repository).execute(
                null,
                INVITEE_EMAIL,
                PLAINTEXT_TOKEN))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("authenticatedProfileId is required.");

        assertThat(repository.lookupDigest).isNull();
        assertThat(repository.acceptCalls).isZero();
    }

    private static AcceptMemberInvitation service(CapturingRepository repository) {
        return new AcceptMemberInvitation(repository);
    }

    private static CapturingRepository repository() {
        CapturingRepository repository = new CapturingRepository();
        repository.pending = new PersistedMemberInvitation(
                INVITATION_ID,
                new MemberInvitation(
                        PROJECT_ID,
                        INVITEE_EMAIL,
                        null,
                        "MEMBER",
                        "INVITED",
                        CREATOR_ID,
                        LIMITS));
        return repository;
    }

    private static final class CapturingRepository
            implements MemberInvitationAcceptanceRepository {

        private PersistedMemberInvitation pending;
        private boolean digestAvailable = true;
        private String lookupDigest;
        private String acceptedDigest;
        private UUID acceptedProfileId;
        private boolean membershipExists;
        private int acceptCalls;

        @Override
        public Optional<PersistedMemberInvitation> findByInviteTokenDigest(String digest) {
            lookupDigest = digest;
            if (TOKEN_DIGEST.equals(digest)
                    && pending != null
                    && digestAvailable
                    && (pending.invitation().membershipStatus().equals("INVITED")
                            || pending.invitation().membershipStatus().equals("ACTIVE"))) {
                return Optional.of(pending);
            }
            return Optional.empty();
        }

        @Override
        public boolean hasMembership(UUID projectId, UUID profileId) {
            return membershipExists;
        }

        @Override
        public Optional<PersistedMemberInvitation> acceptInvitation(
                UUID invitationId,
                String digest,
                UUID profileId) {
            acceptCalls++;
            acceptedDigest = digest;
            acceptedProfileId = profileId;
            if (pending == null || !"INVITED".equals(pending.invitation().membershipStatus())) {
                return Optional.empty();
            }
            pending = new PersistedMemberInvitation(
                    pending.id(),
                    pending.invitation().accept(profileId));
            digestAvailable = false;
            return Optional.of(pending);
        }
    }
}
