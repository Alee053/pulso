package com.pulso.backend.project.domain;

public class MembershipPermissionException extends RuntimeException {

    public MembershipPermissionException() {
        super("active OWNER or ADMIN membership is required.");
    }

    public MembershipPermissionException(String message) {
        super(message);
    }
}
