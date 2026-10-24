package com.pulso.backend.matching.domain;

import java.util.List;
import java.util.UUID;

public record OpportunityMatch(
        UUID opportunityId,
        String title,
        String opportunityType,
        int score,
        boolean onboardingCompleted,
        List<MatchFactor> factors) {

    public OpportunityMatch {
        factors = factors == null ? List.of() : List.copyOf(factors);
    }

    public List<MatchFactorKey> missingProfileFactors() {
        return factors.stream()
                .filter(factor -> factor.status() == MatchFactorStatus.MISSING_PROFILE_DATA)
                .map(MatchFactor::key)
                .toList();
    }
}
