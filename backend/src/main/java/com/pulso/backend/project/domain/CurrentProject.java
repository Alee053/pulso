package com.pulso.backend.project.domain;

import java.util.UUID;

public record CurrentProject(UUID projectId, String name, String role, String membershipStatus) {
}
