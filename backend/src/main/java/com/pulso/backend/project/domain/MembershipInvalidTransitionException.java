package com.pulso.backend.project.domain;

public class MembershipInvalidTransitionException extends RuntimeException {

    public MembershipInvalidTransitionException(String message) {
        super(message);
    }
}
