package com.pulso.backend.project.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.InviteTokenDigest;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;
import com.pulso.backend.project.domain.MemberInvitationPermissionException;

class MemberInvitationUseCasesTest {
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID INVITEE = UUID.randomUUID();

    @Test void ownerCreatesAnInviteWithoutReturningAPlaintextToken() {
        Fake repo = new Fake();
        String[] delivered = new String[1];
        var created = new CreateMemberInvitation(repo, (email, token) -> delivered[0] = token)
                .execute(OWNER, PROJECT, " ADA@EXAMPLE.COM ");
        assertThat(created.invitation().membershipStatus()).isEqualTo("INVITED");
        assertThat(created.invitation().inviteeEmail()).isEqualTo("ada@example.com");
        assertThat(repo.digest).isEqualTo(InviteTokenDigest.fromPlaintext(delivered[0]));
        assertThat(created.toString()).doesNotContain(delivered[0]);
    }
    @Test void memberCannotCreateInvite() {
        Fake repo = new Fake(); repo.role = "MEMBER";
        assertThatThrownBy(() -> new CreateMemberInvitation(repo, (email, token) -> {})
                .execute(OWNER, PROJECT, "ada@example.com"))
                .isInstanceOf(MemberInvitationPermissionException.class);
    }
    @Test void inviteeCanAcceptAndReplayFails() {
        Fake repo = new Fake();
        String[] token = new String[1];
        new CreateMemberInvitation(repo, (email, t) -> token[0] = t)
                .execute(OWNER, PROJECT, "ada@example.com");
        var result = new AcceptMemberInvitation(repo).execute(INVITEE, "ada@example.com", token[0]);
        assertThat(result.invitation().membershipStatus()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> new AcceptMemberInvitation(repo).execute(INVITEE, "ada@example.com", token[0]))
                .isInstanceOf(MemberInvitationNotFoundException.class);
    }
    @Test void wrongEmailCannotAccept() {
        Fake repo = new Fake(); String[] token = new String[1];
        new CreateMemberInvitation(repo, (email,t) -> token[0]=t).execute(OWNER,PROJECT,"ada@example.com");
        assertThatThrownBy(() -> new AcceptMemberInvitation(repo).execute(INVITEE,"other@example.com",token[0]))
                .isInstanceOf(MemberInvitationIdentityException.class);
    }
    private static class Fake implements MemberInvitationRepository {
        String role = "OWNER"; String digest; MemberInvitation invitation; UUID id = UUID.randomUUID();
        public boolean projectExists(UUID p) { return true; }
        public Optional<MemberInvitationActor> findActor(UUID p, UUID a) { return Optional.of(new MemberInvitationActor(p,a,role,"ACTIVE")); }
        public List<MemberInvitation> findMembers(UUID p) { return invitation == null ? List.of() : List.of(invitation); }
        public boolean isActiveAccountEmail(UUID p,String e) { return false; }
        public UUID save(CreatedMemberInvitation c) { invitation=c.invitation();digest=c.inviteTokenDigest();return id; }
        public Optional<StoredMemberInvitation> findByDigest(String d) { return digest != null && digest.equals(d) ? Optional.of(new StoredMemberInvitation(id,invitation)) : Optional.empty(); }
        public boolean hasMembership(UUID p,UUID a) { return false; }
        public boolean activate(UUID id,UUID profile) { if(invitation == null || !invitation.membershipStatus().equals("INVITED"))return false; invitation=invitation.accept(profile);digest=null;return true; }
    }
}
