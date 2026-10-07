package com.pulso.backend.project.application;

public class MemberInvitationIdentityException extends RuntimeException {
    public MemberInvitationIdentityException() { super("sign in with the invited email address."); }
}
