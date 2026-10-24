package com.pulso.backend.matching.domain;

import java.util.List;

/**
 * One explainable comparison between a person and an opportunity.
 *
 * @param weight points this factor can contribute; zero when it is not applicable
 * @param points points actually earned, never more than {@code weight}
 * @param matched names of the compared items that coincide
 * @param unmatched names of the opportunity or person items that did not coincide
 * @param reason optional machine-readable code that qualifies the result
 */
public record MatchFactor(
        MatchFactorKey key,
        MatchFactorStatus status,
        int weight,
        int points,
        List<String> matched,
        List<String> unmatched,
        String reason) {

    public MatchFactor {
        matched = matched == null ? List.of() : List.copyOf(matched);
        unmatched = unmatched == null ? List.of() : List.copyOf(unmatched);
    }
}
