package com.pulso.backend.matching.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.matching.application.GetOpportunityMatches;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/projects/{projectRef}/opportunity-matches")
public class OpportunityMatchController {

    private final GetOpportunityMatches getOpportunityMatches;

    public OpportunityMatchController(GetOpportunityMatches getOpportunityMatches) {
        this.getOpportunityMatches = getOpportunityMatches;
    }

    @GetMapping
    public OpportunityMatchListResponse list(
            @PathVariable String projectRef,
            @AuthenticationPrincipal Jwt jwt) {
        return OpportunityMatchListResponse.from(
                getOpportunityMatches.execute(projectRef, authenticatedProfileId(jwt)));
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
