package com.pulso.backend.project.application;

import com.pulso.backend.config.ApiMessages;

public class MemberInvitationNotFoundException extends RuntimeException {

    public MemberInvitationNotFoundException() {
        super(ApiMessages.INVITATION_NOT_FOUND_DETAIL);
    }
}
