package com.pulso.backend.matching.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.matching.domain.MatchOpportunity;
import com.pulso.backend.matching.domain.MatchProfile;

public interface OpportunityMatchRepository {

    /**
     * Open opportunities of a published project with an active sector, newest first,
     * or empty when the project is not publicly available.
     */
    Optional<List<MatchOpportunity>> findOpenOpportunities(String projectRef);

    Optional<MatchProfile> findProfile(UUID profileId);
}
