package com.pulso.backend.project.domain;

public class MemberInvitationPermissionException extends RuntimeException {

    public MemberInvitationPermissionException() {
        super("active OWNER or ADMIN membership is required.");
    }
}
