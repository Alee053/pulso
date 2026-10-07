package com.pulso.backend.project.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;
import com.pulso.backend.project.domain.MemberInvitationPermissionException;
import com.pulso.backend.project.domain.MemberInvitationProjectNotFoundException;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class CreateMemberInvitation {

    private final MemberInvitationRepository repository;
    private final MemberInvitationTokenGenerator tokenGenerator;
    private final MemberInvitationDelivery delivery;
    private final MemberInvitation.Limits limits;

    public CreateMemberInvitation(
            MemberInvitationRepository repository,
            MemberInvitationTokenGenerator tokenGenerator,
            ProjectProperties properties,
            MemberInvitationDelivery delivery) {
        this.repository = repository;
        this.tokenGenerator = tokenGenerator;
        this.delivery = delivery;
        this.limits = new MemberInvitation.Limits(properties.inviteeEmailMaxLength());
    }

    @Transactional
    public PersistedMemberInvitation execute(
            UUID projectId,
            UUID authenticatedProfileId,
            String inviteeEmail) {
        requireIdentifiers(projectId, authenticatedProfileId);

        if (!repository.projectExists(projectId)) {
            throw new MemberInvitationProjectNotFoundException();
        }

        MemberInvitationActor actor = repository.findActor(projectId, authenticatedProfileId)
                .orElseThrow(MemberInvitationPermissionException::new);
        String token = tokenGenerator.generate();
        CreatedMemberInvitation created = MemberInvitation.invite(
                projectId,
                true,
                actor,
                inviteeEmail,
                token,
                repository.findInvitations(projectId),
                limits);
        PersistedMemberInvitation persisted = repository.createInvitation(created);
        delivery.send(created.invitation().inviteeEmail(), token);
        return persisted;
    }

    private static void requireIdentifiers(UUID projectId, UUID authenticatedProfileId) {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
    }
}
