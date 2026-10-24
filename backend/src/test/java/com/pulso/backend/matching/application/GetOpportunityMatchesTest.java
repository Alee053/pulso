package com.pulso.backend.matching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.matching.domain.MatchOpportunity;
import com.pulso.backend.matching.domain.MatchProfile;
import com.pulso.backend.matching.domain.OpportunityMatch;
import com.pulso.backend.profile.application.ProfileNotFoundException;
import com.pulso.backend.project.application.PublicProjectNotFoundException;

class GetOpportunityMatchesTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final MatchOpportunity OPPORTUNITY = new MatchOpportunity(
            UUID.fromString("55555555-5555-5555-5555-555555555555"),
            "Disenador",
            "TALENT",
            null,
            null,
            false,
            List.of(),
            new MatchOpportunity.Sector(
                    UUID.fromString("66666666-6666-6666-6666-666666666666"), "sostenibilidad", "Sostenibilidad"),
            "MVP",
            List.of());
    private static final MatchProfile PROFILE = new MatchProfile(
            true, Set.of("WORK"), Set.of("sostenibilidad"), Set.of("MVP"), Set.of(), Set.of(), null, null);

    @Test
    void matchesEveryOpenOpportunityAgainstTheAuthenticatedProfile() {
        GetOpportunityMatches useCase = new GetOpportunityMatches(
                repository(Optional.of(List.of(OPPORTUNITY)), Optional.of(PROFILE)));

        List<OpportunityMatch> matches = useCase.execute("eco-pack", PROFILE_ID);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).opportunityId()).isEqualTo(OPPORTUNITY.id());
        assertThat(matches.get(0).score()).isEqualTo(100);
    }

    @Test
    void returnsAnEmptyListForAProjectWithoutOpenOpportunities() {
        GetOpportunityMatches useCase = new GetOpportunityMatches(
                repository(Optional.of(List.of()), Optional.of(PROFILE)));

        assertThat(useCase.execute("eco-pack", PROFILE_ID)).isEmpty();
    }

    @Test
    void treatsAnUnavailableProjectAsNotFound() {
        GetOpportunityMatches useCase = new GetOpportunityMatches(
                repository(Optional.empty(), Optional.of(PROFILE)));

        assertThatThrownBy(() -> useCase.execute("draft-project", PROFILE_ID))
                .isInstanceOf(PublicProjectNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(" ", PROFILE_ID))
                .isInstanceOf(PublicProjectNotFoundException.class);
    }

    @Test
    void rejectsAProfileThatDoesNotExist() {
        GetOpportunityMatches useCase = new GetOpportunityMatches(
                repository(Optional.of(List.of(OPPORTUNITY)), Optional.empty()));

        assertThatThrownBy(() -> useCase.execute("eco-pack", PROFILE_ID))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    private static OpportunityMatchRepository repository(
            Optional<List<MatchOpportunity>> opportunities,
            Optional<MatchProfile> profile) {
        return new OpportunityMatchRepository() {
            @Override
            public Optional<List<MatchOpportunity>> findOpenOpportunities(String projectRef) {
                return opportunities;
            }

            @Override
            public Optional<MatchProfile> findProfile(UUID profileId) {
                assertThat(profileId).isEqualTo(PROFILE_ID);
                return profile;
            }
        };
    }
}
