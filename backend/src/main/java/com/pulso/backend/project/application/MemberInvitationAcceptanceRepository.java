package com.pulso.backend.project.application;

import java.util.Optional;
import java.util.UUID;

public interface MemberInvitationAcceptanceRepository {

    Optional<PersistedMemberInvitation> findByInviteTokenDigest(String inviteTokenDigest);

    boolean hasMembership(UUID projectId, UUID profileId);

    Optional<PersistedMemberInvitation> acceptInvitation(
            UUID invitationId,
            String inviteTokenDigest,
            UUID profileId);
}
