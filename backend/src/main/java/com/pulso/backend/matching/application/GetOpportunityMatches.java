package com.pulso.backend.matching.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pulso.backend.matching.domain.MatchOpportunity;
import com.pulso.backend.matching.domain.MatchProfile;
import com.pulso.backend.matching.domain.OpportunityMatch;
import com.pulso.backend.matching.domain.OpportunityMatcher;
import com.pulso.backend.profile.application.ProfileNotFoundException;
import com.pulso.backend.project.application.PublicProjectNotFoundException;

@Service
public class GetOpportunityMatches {

    private final OpportunityMatchRepository repository;

    public GetOpportunityMatches(OpportunityMatchRepository repository) {
        this.repository = repository;
    }

    public List<OpportunityMatch> execute(String projectRef, UUID profileId) {
        if (projectRef == null || projectRef.isBlank()) {
            throw new PublicProjectNotFoundException();
        }

        List<MatchOpportunity> opportunities = repository.findOpenOpportunities(projectRef)
                .orElseThrow(PublicProjectNotFoundException::new);
        MatchProfile profile = repository.findProfile(profileId)
                .orElseThrow(ProfileNotFoundException::new);

        return opportunities.stream()
                .map(opportunity -> OpportunityMatcher.match(profile, opportunity))
                .toList();
    }
}
