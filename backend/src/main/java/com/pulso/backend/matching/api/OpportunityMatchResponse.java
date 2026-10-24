package com.pulso.backend.matching.api;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.matching.domain.MatchFactor;
import com.pulso.backend.matching.domain.MatchFactorKey;
import com.pulso.backend.matching.domain.MatchFactorStatus;
import com.pulso.backend.matching.domain.OpportunityMatch;

public record OpportunityMatchResponse(
        UUID opportunityId,
        String title,
        String opportunityType,
        int score,
        boolean onboardingCompleted,
        List<MatchFactorKey> missingFactors,
        List<Factor> factors) {

    public static OpportunityMatchResponse from(OpportunityMatch match) {
        return new OpportunityMatchResponse(
                match.opportunityId(),
                match.title(),
                match.opportunityType(),
                match.score(),
                match.onboardingCompleted(),
                match.missingProfileFactors(),
                match.factors().stream().map(Factor::from).toList());
    }

    public record Factor(
            MatchFactorKey key,
            MatchFactorStatus status,
            int weight,
            int points,
            List<String> matched,
            List<String> unmatched,
            String reason) {

        static Factor from(MatchFactor factor) {
            return new Factor(
                    factor.key(),
                    factor.status(),
                    factor.weight(),
                    factor.points(),
                    factor.matched(),
                    factor.unmatched(),
                    factor.reason());
        }
    }
}
