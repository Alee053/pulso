package com.pulso.backend.project.domain;

import com.pulso.backend.config.ApiMessages;

public class DuplicateMemberInvitationException extends RuntimeException {

    public DuplicateMemberInvitationException() {
        super(ApiMessages.DUPLICATE_INVITATION);
    }
}
