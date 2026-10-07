package com.pulso.backend.project.application;

import com.pulso.backend.config.ApiMessages;

public class MembershipConflictException extends RuntimeException {

    public MembershipConflictException() {
        super(ApiMessages.MEMBERSHIP_CONFLICT_DETAIL);
    }
}
