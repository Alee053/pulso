package com.pulso.backend.interaction.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

/** Stable keyset position for the discovery feed's preference/date/id ordering. */
public record DiscoveryFeedCursor(
        int preferenceScore,
        OffsetDateTime publishedAt,
        UUID projectId,
        int offset) {

    public DiscoveryFeedCursor {
        if (preferenceScore < 0 || preferenceScore > 2 || publishedAt == null
                || projectId == null || offset < 0) {
            throw new IllegalArgumentException("Discovery feed cursor is invalid.");
        }
    }

    public String encode() {
        String value = preferenceScore + "|" + publishedAt.toInstant() + "|" + projectId + "|" + offset;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static DiscoveryFeedCursor decode(String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException("Discovery feed cursor is invalid.");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\|", -1);
            if (fields.length != 4) {
                throw new IllegalArgumentException("Discovery feed cursor is invalid.");
            }
            return new DiscoveryFeedCursor(
                    Integer.parseInt(fields[0]),
                    Instant.parse(fields[1]).atOffset(ZoneOffset.UTC),
                    UUID.fromString(fields[2]),
                    Integer.parseInt(fields[3]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Discovery feed cursor is invalid.", exception);
        }
    }
}
