package com.pulso.backend.profile.application;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException() {
        super("Authenticated profile was not found.");
    }
}
