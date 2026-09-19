package com.pulso.backend.project.domain;

public class MemberInvitationProjectNotFoundException extends RuntimeException {

    public MemberInvitationProjectNotFoundException() {
        super("project does not exist.");
    }
}
