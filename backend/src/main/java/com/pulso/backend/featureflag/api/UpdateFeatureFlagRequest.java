package com.pulso.backend.featureflag.api;

import tools.jackson.databind.JsonNode;

public record UpdateFeatureFlagRequest(JsonNode enabled) {
}
