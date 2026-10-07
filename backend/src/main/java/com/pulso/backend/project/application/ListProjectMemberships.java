package com.pulso.backend.project.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.project.domain.ManagedProjectMember;
import com.pulso.backend.project.domain.MembershipAdministration;
import com.pulso.backend.project.domain.MembershipPermissionException;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class ListProjectMemberships {

    private final MembershipAdministrationRepository repository;

    public ListProjectMemberships(MembershipAdministrationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ManagedProjectMember> execute(
            UUID projectId,
            UUID authenticatedProfileId,
            int offset,
            int limit) {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
        var actor = repository.findActor(projectId, authenticatedProfileId)
                .orElseThrow(MembershipPermissionException::new);
        if (!MembershipAdministration.canAdminister(actor)) {
            throw new MembershipPermissionException();
        }
        return repository.findManagedMembers(projectId, offset, limit);
    }
}
