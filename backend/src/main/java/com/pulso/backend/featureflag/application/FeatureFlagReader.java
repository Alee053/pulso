package com.pulso.backend.featureflag.application;

import com.pulso.backend.featureflag.domain.FeatureFlagKey;

public interface FeatureFlagReader {

    boolean isEnabled(FeatureFlagKey key);
}
