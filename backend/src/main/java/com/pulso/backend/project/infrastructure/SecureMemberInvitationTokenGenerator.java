package com.pulso.backend.project.infrastructure;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.pulso.backend.project.application.MemberInvitationTokenGenerator;

@Component
public class SecureMemberInvitationTokenGenerator implements MemberInvitationTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
