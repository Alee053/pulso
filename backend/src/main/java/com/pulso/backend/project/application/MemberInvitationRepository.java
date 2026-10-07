package com.pulso.backend.project.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;

public interface MemberInvitationRepository {

    boolean projectExists(UUID projectId);

    Optional<MemberInvitationActor> findActor(UUID projectId, UUID profileId);

    List<MemberInvitation> findInvitations(UUID projectId);

    PersistedMemberInvitation createInvitation(CreatedMemberInvitation invitation);
}
