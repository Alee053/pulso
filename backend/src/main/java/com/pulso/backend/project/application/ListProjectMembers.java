package com.pulso.backend.project.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.project.domain.MembershipPermissionException;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class ListProjectMembers {

    private final ProjectMembersReader reader;

    public ListProjectMembers(ProjectMembersReader reader) {
        this.reader = reader;
    }

    public ProjectMembersSnapshot execute(UUID projectId, UUID profileId, int offset, int limit) {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (profileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
        return reader.findAuthorizedPage(projectId, profileId, offset, limit)
                .orElseThrow(() -> new MembershipPermissionException(ApiMessages.MEMBERSHIP_READ_PERMISSION_DETAIL));
    }
}
