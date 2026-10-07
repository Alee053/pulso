package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.InviteTokenDigest;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;

class MemberInvitationUseCasesTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INVITATION_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final String TOKEN = "accept-me";
    private static final ProjectProperties PROPERTIES =
            new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254);

    @Test
    void sendsThePlaintextTokenAfterPersistingOnlyItsDigest() {
        CapturingRepository repository = new CapturingRepository();
        String[] sentAddress = new String[1];
        String[] sentToken = new String[1];
        MemberInvitationDelivery delivery = (address, token) -> {
            sentAddress[0] = address;
            sentToken[0] = token;
        };

        PersistedMemberInvitation result = new CreateMemberInvitation(
                repository,
                () -> TOKEN,
                PROPERTIES,
                delivery).execute(PROJECT_ID, OWNER_ID, " Ada@Example.COM ");

        assertThat(result.invitation().inviteeEmail()).isEqualTo("ada@example.com");
        assertThat(result.invitation().membershipStatus()).isEqualTo("INVITED");
        assertThat(sentAddress[0]).isEqualTo("ada@example.com");
        assertThat(sentToken[0]).isEqualTo(TOKEN);
        assertThat(repository.persistedInvitation.inviteTokenDigest())
                .isEqualTo(InviteTokenDigest.fromPlaintext(TOKEN));
        assertThat(result.toString()).doesNotContain(TOKEN);
    }

    private static final class CapturingRepository implements MemberInvitationRepository {

        private final MemberInvitationActor actor = new MemberInvitationActor(
                PROJECT_ID,
                OWNER_ID,
                "OWNER",
                "ACTIVE");
        private CreatedMemberInvitation persistedInvitation;

        @Override
        public boolean projectExists(UUID projectId) {
            return PROJECT_ID.equals(projectId);
        }

        @Override
        public Optional<MemberInvitationActor> findActor(UUID projectId, UUID profileId) {
            return PROJECT_ID.equals(projectId) && OWNER_ID.equals(profileId)
                    ? Optional.of(actor)
                    : Optional.empty();
        }

        @Override
        public List<MemberInvitation> findInvitations(UUID projectId) {
            return List.of();
        }

        @Override
        public PersistedMemberInvitation createInvitation(CreatedMemberInvitation invitation) {
            persistedInvitation = invitation;
            return new PersistedMemberInvitation(INVITATION_ID, invitation.invitation());
        }
    }
}
