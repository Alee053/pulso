package com.pulso.backend.featureflag.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.featureflag.application.FeatureFlagValidationException;
import com.pulso.backend.featureflag.application.GetFeatureFlags;
import com.pulso.backend.featureflag.application.SetFeatureFlag;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/feature-flags")
public class FeatureFlagController {

    private final GetFeatureFlags getFeatureFlags;
    private final SetFeatureFlag setFeatureFlag;

    public FeatureFlagController(GetFeatureFlags getFeatureFlags, SetFeatureFlag setFeatureFlag) {
        this.getFeatureFlags = getFeatureFlags;
        this.setFeatureFlag = setFeatureFlag;
    }

    @GetMapping
    public FeatureFlagListResponse list() {
        return FeatureFlagListResponse.from(getFeatureFlags.execute());
    }

    @PutMapping("/{key}")
    public FeatureFlagResponse update(
            @PathVariable String key,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) UpdateFeatureFlagRequest request) {
        if (request == null) {
            throw new FeatureFlagValidationException();
        }
        if (request.enabled() == null || !request.enabled().isBoolean()) {
            throw new FeatureFlagValidationException();
        }
        return FeatureFlagResponse.from(setFeatureFlag.execute(
                authenticatedProfileId(jwt), key, request.enabled().booleanValue()));
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidAuthenticatedSubjectException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }
}
