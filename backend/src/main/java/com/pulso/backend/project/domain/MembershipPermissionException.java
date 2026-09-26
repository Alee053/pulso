package com.pulso.backend.project.domain;

import com.pulso.backend.config.ApiMessages;

public class MembershipPermissionException extends RuntimeException {

    public MembershipPermissionException() {
        super(ApiMessages.MEMBERSHIP_PERMISSION_DETAIL);
    }

    public MembershipPermissionException(String message) {
        super(message);
    }
}
