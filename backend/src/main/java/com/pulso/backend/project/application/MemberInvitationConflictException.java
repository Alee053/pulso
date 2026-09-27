package com.pulso.backend.project.application;

public class MemberInvitationConflictException extends RuntimeException {
    public MemberInvitationConflictException() { super("the account is already a member of this project."); }
}
