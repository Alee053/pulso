package com.pulso.backend.project.domain;

import com.pulso.backend.config.ApiMessages;

public class MemberInvitationProjectNotFoundException extends RuntimeException {

    public MemberInvitationProjectNotFoundException() {
        super(ApiMessages.INVITATION_PROJECT_NOT_FOUND);
    }
}
