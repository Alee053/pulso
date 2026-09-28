package com.pulso.backend.project.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.project.domain.InviteTokenDigest;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class AcceptMemberInvitation {

    private final MemberInvitationAcceptanceRepository repository;

    public AcceptMemberInvitation(MemberInvitationAcceptanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PersistedMemberInvitation execute(
            UUID authenticatedProfileId,
            String authenticatedEmail,
            String token) {
        requireAuthenticatedProfile(authenticatedProfileId);

        String inviteTokenDigest = InviteTokenDigest.fromPlaintext(token);
        PersistedMemberInvitation persisted = repository
                .findByInviteTokenDigest(inviteTokenDigest)
                .orElseThrow(MemberInvitationNotFoundException::new);
        MemberInvitation invitation = persisted.invitation();

        if (!"INVITED".equals(invitation.membershipStatus())
                || invitation.profileId() != null
                || !invitation.matchesInviteeEmail(authenticatedEmail)) {
            throw new MemberInvitationNotFoundException();
        }
        if (repository.hasMembership(invitation.projectId(), authenticatedProfileId)) {
            throw new MemberInvitationNotFoundException();
        }

        invitation.accept(authenticatedProfileId);
        return repository.acceptInvitation(
                        persisted.id(),
                        inviteTokenDigest,
                        authenticatedProfileId)
                .orElseThrow(MemberInvitationNotFoundException::new);
    }

    private static void requireAuthenticatedProfile(UUID authenticatedProfileId) {
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
    }
}
