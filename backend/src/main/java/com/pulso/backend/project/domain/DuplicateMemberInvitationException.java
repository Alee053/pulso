package com.pulso.backend.project.domain;

public class DuplicateMemberInvitationException extends RuntimeException {

    public DuplicateMemberInvitationException() {
        super("an active invitation already exists for this project and email.");
    }
}
