package com.pulso.backend.featureflag.application;

import java.util.List;
import java.util.Optional;

import com.pulso.backend.featureflag.domain.FeatureFlag;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;

public interface FeatureFlagRepository extends FeatureFlagReader {

    List<FeatureFlag> findAll();

    Optional<FeatureFlag> findByKey(FeatureFlagKey key);

    Optional<FeatureFlag> update(FeatureFlagKey key, boolean enabled);
}
