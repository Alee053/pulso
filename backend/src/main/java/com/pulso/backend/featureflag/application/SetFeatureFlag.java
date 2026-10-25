package com.pulso.backend.featureflag.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.featureflag.domain.FeatureFlag;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;
import com.pulso.backend.profile.application.ProfileRepository;

@Service
public class SetFeatureFlag {

    private final FeatureFlagRepository featureFlags;
    private final ProfileRepository profiles;

    public SetFeatureFlag(FeatureFlagRepository featureFlags, ProfileRepository profiles) {
        this.featureFlags = featureFlags;
        this.profiles = profiles;
    }

    @Transactional
    public FeatureFlag execute(UUID authenticatedProfileId, String key, boolean enabled) {
        FeatureFlagKey flagKey = FeatureFlagKey.from(key)
                .orElseThrow(FeatureFlagNotFoundException::new);
        boolean administrator = authenticatedProfileId != null
                && profiles.findById(authenticatedProfileId)
                        .map(profile -> "ADMIN".equals(profile.platformRole()))
                        .orElse(false);
        if (!administrator) {
            throw new FeatureFlagAuthorizationException();
        }

        return featureFlags.update(flagKey, enabled)
                .orElseThrow(FeatureFlagNotFoundException::new);
    }
}
