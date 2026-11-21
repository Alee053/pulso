package com.pulso.backend.profile.domain;

import java.util.UUID;

public record BlockedProfile(UUID profileId, String displayName) {
}
