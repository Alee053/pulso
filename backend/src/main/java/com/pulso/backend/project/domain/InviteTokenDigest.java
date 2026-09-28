package com.pulso.backend.project.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.pulso.backend.config.ApiMessages;

public final class InviteTokenDigest {

    private InviteTokenDigest() {
    }

    public static String fromPlaintext(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) {
            throw new ProjectValidationException(ApiMessages.INVITE_TOKEN_REQUIRED);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(plaintextToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(ApiMessages.SHA256_REQUIRED, exception);
        }
    }

    public static String require(String digest) {
        if (digest == null || digest.isBlank()) {
            throw new ProjectValidationException(ApiMessages.INVITE_TOKEN_DIGEST_REQUIRED);
        }
        String normalized = digest.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64 || !normalized.chars().allMatch(InviteTokenDigest::isHex)) {
            throw new ProjectValidationException(ApiMessages.INVITE_TOKEN_DIGEST_INVALID);
        }
        return normalized;
    }

    private static boolean isHex(int character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f');
    }
}
