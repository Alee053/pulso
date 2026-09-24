package com.pulso.backend.project.domain;

import com.pulso.backend.config.ApiMessages;

public class MemberInvitationPermissionException extends RuntimeException {

    public MemberInvitationPermissionException() {
        super(ApiMessages.MEMBERSHIP_PERMISSION_DETAIL);
    }
}
