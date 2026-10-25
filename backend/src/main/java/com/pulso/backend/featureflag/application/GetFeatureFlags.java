package com.pulso.backend.featureflag.application;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.pulso.backend.featureflag.domain.FeatureFlag;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;

@Service
public class GetFeatureFlags {

    private final FeatureFlagRepository repository;

    public GetFeatureFlags(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    public List<FeatureFlag> execute() {
        return Arrays.stream(FeatureFlagKey.values())
                .map(key -> repository.findByKey(key)
                        .orElseGet(() -> new FeatureFlag(key, key.defaultEnabled())))
                .toList();
    }
}
