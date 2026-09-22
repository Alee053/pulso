package com.pulso.backend.project.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class InviteTokenDigest {

    private InviteTokenDigest() {
    }

    public static String fromPlaintext(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) {
            throw new ProjectValidationException("invite token is required.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(plaintextToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to digest invite tokens.", exception);
        }
    }

    public static String require(String digest) {
        if (digest == null || digest.isBlank()) {
            throw new ProjectValidationException("inviteTokenDigest is required.");
        }
        String normalized = digest.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64 || !normalized.chars().allMatch(InviteTokenDigest::isHex)) {
            throw new ProjectValidationException("inviteTokenDigest must be a SHA-256 hex digest.");
        }
        return normalized;
    }

    private static boolean isHex(int character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f');
    }
}
