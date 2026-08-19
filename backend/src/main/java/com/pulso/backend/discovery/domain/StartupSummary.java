package com.pulso.backend.discovery.domain;

import java.util.UUID;

public record StartupSummary(
        UUID id,
        String slug,
        String name,
        String tagline,
        String sector,
        String city,
        String stage) {
}
