package com.pulso.backend.project.application;

public class MemberInvitationNotFoundException extends RuntimeException {
    public MemberInvitationNotFoundException() { super("invitation not found or already used."); }
}
