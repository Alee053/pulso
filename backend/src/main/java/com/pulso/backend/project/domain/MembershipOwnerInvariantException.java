package com.pulso.backend.project.domain;

import com.pulso.backend.config.ApiMessages;

public class MembershipOwnerInvariantException extends RuntimeException {

    public MembershipOwnerInvariantException() {
        super(ApiMessages.MEMBERSHIP_OWNER_REQUIRED_DETAIL);
    }
}
