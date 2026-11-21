package com.pulso.backend.profile.api;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

public final class AuthenticatedProfileId {

    private AuthenticatedProfileId() {
    }

    public static UUID require(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidAuthenticatedSubjectException();
        }
        try {
            String subject = jwt.getSubject();
            UUID profileId = UUID.fromString(subject);
            if (profileId.toString().equalsIgnoreCase(subject)) {
                return profileId;
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
        throw new InvalidAuthenticatedSubjectException();
    }
}
