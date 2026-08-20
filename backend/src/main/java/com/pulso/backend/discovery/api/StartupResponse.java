package com.pulso.backend.discovery.api;

import java.util.UUID;

import com.pulso.backend.discovery.domain.StartupSummary;

public record StartupResponse(
        UUID id,
        String slug,
        String name,
        String tagline,
        String sector,
        String city,
        String stage) {

    public static StartupResponse from(StartupSummary startup) {
        return new StartupResponse(
                startup.id(),
                startup.slug(),
                startup.name(),
                startup.tagline(),
                startup.sector(),
                startup.city(),
                startup.stage());
    }
}
