package com.pulso.backend.project.application;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service

public class CreateMemberInvitation {
    private final MemberInvitationRepository repository;
    private final MemberInvitationDelivery delivery;
    private final SecureRandom random = new SecureRandom();
    private final ProjectProperties projectProperties;

    public CreateMemberInvitation(MemberInvitationRepository repository,
                                MemberInvitationDelivery delivery,
                                ProjectProperties projectProperties) {
        this.repository = repository;
        this.delivery = delivery;
        this.projectProperties = projectProperties;
    }

    // Delivery is deliberately part of the operation: failed delivery must not be reported as success.
    // SMTP is not transactional; a production deployment should replace it with an outbox when needed.
    @org.springframework.transaction.annotation.Transactional
    public CreatedInvitationResult execute(UUID actorId, UUID projectId, String email) {
        if (actorId == null || projectId == null) {
            throw new ProjectValidationException("actor and projectId are required.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        CreatedMemberInvitation created = MemberInvitation.invite(
                projectId, repository.projectExists(projectId),
                repository.findActor(projectId, actorId).orElse(null),
                email, token, repository.findMembers(projectId),
                new MemberInvitation.Limits(projectProperties.inviteeEmailMaxLength()));
        if (repository.isActiveAccountEmail(projectId, created.invitation().inviteeEmail())) {
            throw new MemberInvitationConflictException();
        }
        UUID id = repository.save(created);
        delivery.send(created.invitation().inviteeEmail(), token);
        return new CreatedInvitationResult(id, created.invitation());
    }
}