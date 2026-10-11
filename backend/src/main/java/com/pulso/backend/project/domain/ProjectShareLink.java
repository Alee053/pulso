package com.pulso.backend.project.domain;

import java.util.UUID;

public record ProjectShareLink(
        UUID projectId,
        String slug,
        String url) {
}