package com.pulso.backend.matching.api;

import java.util.List;

import com.pulso.backend.matching.domain.OpportunityMatch;

public record OpportunityMatchListResponse(List<OpportunityMatchResponse> data) {

    public static OpportunityMatchListResponse from(List<OpportunityMatch> matches) {
        return new OpportunityMatchListResponse(
                matches.stream().map(OpportunityMatchResponse::from).toList());
    }
}
