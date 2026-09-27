package com.pulso.backend.project.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pulso.backend.project.domain.InviteTokenDigest;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class AcceptMemberInvitation {
    private final MemberInvitationRepository repository;

    public AcceptMemberInvitation(MemberInvitationRepository repository) { this.repository = repository; }

    @Transactional
    public AcceptedInvitationResult execute(UUID profileId, String authenticatedEmail, String token) {
        if (profileId == null || authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new MemberInvitationIdentityException();
        }
        if (token == null || token.isBlank()) {
            throw new ProjectValidationException("token is required.");
        }
        StoredMemberInvitation stored = repository.findByDigest(InviteTokenDigest.fromPlaintext(token))
                .orElseThrow(MemberInvitationNotFoundException::new);
        MemberInvitation pending = stored.invitation();
        if (!"INVITED".equals(pending.membershipStatus())) {
            throw new MemberInvitationNotFoundException();
        }
        if (!pending.inviteeEmail().equals(authenticatedEmail.trim().toLowerCase(Locale.ROOT))) {
            throw new MemberInvitationIdentityException();
        }
        if (repository.hasMembership(pending.projectId(), profileId)) {
            throw new MemberInvitationConflictException();
        }
        MemberInvitation accepted = pending.accept(profileId);
        if (!repository.activate(stored.id(), profileId)) {
            throw new MemberInvitationNotFoundException();
        }
        return new AcceptedInvitationResult(stored.id(), accepted);
    }
}
