package com.pulso.backend.project.domain;

public class MembershipOwnerInvariantException extends RuntimeException {

    public MembershipOwnerInvariantException() {
        super("project must keep at least one active OWNER.");
    }
}
