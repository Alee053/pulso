package com.pulso.backend.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OpportunityMatcherTest {

    private static final UUID JAVA_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID FIGMA_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID SQL_ID = UUID.fromString("a0000000-0000-0000-0000-000000000003");
    private static final UUID IMPACT_TAG_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID AGRO_TAG_ID = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID SECTOR_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    private static final MatchOpportunity.Sector SECTOR =
            new MatchOpportunity.Sector(SECTOR_ID, "sostenibilidad", "Sostenibilidad");

    @Test
    void scoresAFullyCompatiblePersonAtOneHundredWithEveryFactorExplained() {
        MatchProfile profile = new MatchProfile(
                true,
                Set.of("WORK"),
                Set.of("sostenibilidad"),
                Set.of("MVP"),
                Set.of(IMPACT_TAG_ID),
                Set.of(JAVA_ID, FIGMA_ID),
                "Cochabamba",
                "Cochabamba");

        OpportunityMatch match = OpportunityMatcher.match(profile, opportunity());

        assertThat(match.score()).isEqualTo(100);
        assertThat(match.onboardingCompleted()).isTrue();
        assertThat(match.missingProfileFactors()).isEmpty();
        assertThat(match.factors()).extracting(MatchFactor::key).containsExactly(
                MatchFactorKey.SKILLS,
                MatchFactorKey.INTENTION,
                MatchFactorKey.INTERESTS,
                MatchFactorKey.SECTOR,
                MatchFactorKey.STAGE,
                MatchFactorKey.LOCATION);
        assertThat(match.factors()).allSatisfy(factor -> {
            assertThat(factor.status()).isEqualTo(MatchFactorStatus.MATCHED);
            assertThat(factor.points()).isEqualTo(factor.weight());
        });
        assertThat(factor(match, MatchFactorKey.SKILLS).matched()).containsExactly("Java", "Figma");
        assertThat(factor(match, MatchFactorKey.INTENTION).matched()).containsExactly("WORK");
        assertThat(factor(match, MatchFactorKey.INTERESTS).matched()).containsExactly("Impacto");
        assertThat(factor(match, MatchFactorKey.SECTOR).matched()).containsExactly("Sostenibilidad");
        assertThat(factor(match, MatchFactorKey.STAGE).matched()).containsExactly("MVP");
        assertThat(factor(match, MatchFactorKey.LOCATION).reason())
                .isEqualTo(OpportunityMatcher.REASON_SAME_CITY);
    }

    @Test
    void answersAPersonWithoutOnboardingWithEveryComparableFactorMarkedMissing() {
        MatchProfile empty = new MatchProfile(false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, null);

        OpportunityMatch match = OpportunityMatcher.match(empty, opportunity());

        assertThat(match.score()).isZero();
        assertThat(match.onboardingCompleted()).isFalse();
        assertThat(match.missingProfileFactors()).containsExactly(
                MatchFactorKey.SKILLS,
                MatchFactorKey.INTENTION,
                MatchFactorKey.INTERESTS,
                MatchFactorKey.SECTOR,
                MatchFactorKey.STAGE,
                MatchFactorKey.LOCATION);
        assertThat(match.factors()).allSatisfy(factor -> {
            assertThat(factor.status()).isEqualTo(MatchFactorStatus.MISSING_PROFILE_DATA);
            assertThat(factor.points()).isZero();
            assertThat(factor.weight()).isPositive();
        });
        assertThat(factor(match, MatchFactorKey.SKILLS).unmatched()).containsExactly("Java", "Figma");
    }

    @Test
    void weighsRequiredSkillsTwiceAsMuchAsPreferredOnes() {
        MatchProfile onlyRequired = profileWithSkills(Set.of(JAVA_ID));
        MatchProfile onlyPreferred = profileWithSkills(Set.of(FIGMA_ID));

        MatchFactor required = factor(OpportunityMatcher.match(onlyRequired, opportunity()), MatchFactorKey.SKILLS);
        MatchFactor preferred = factor(OpportunityMatcher.match(onlyPreferred, opportunity()), MatchFactorKey.SKILLS);

        assertThat(required.status()).isEqualTo(MatchFactorStatus.PARTIAL);
        assertThat(required.points()).isEqualTo(23);
        assertThat(required.matched()).containsExactly("Java");
        assertThat(required.unmatched()).containsExactly("Figma");
        assertThat(preferred.points()).isEqualTo(12);
        assertThat(preferred.unmatched()).containsExactly("Java");
    }

    @Test
    void reportsNoMatchWhenThePersonProvidedDataThatDoesNotCoincide() {
        MatchProfile profile = new MatchProfile(
                true,
                Set.of("INVEST"),
                Set.of("energia"),
                Set.of("IDEA"),
                Set.of(AGRO_TAG_ID),
                Set.of(SQL_ID),
                "La Paz",
                "La Paz");

        OpportunityMatch match = OpportunityMatcher.match(profile, opportunity());

        assertThat(match.score()).isZero();
        assertThat(match.factors()).allSatisfy(factor ->
                assertThat(factor.status()).isEqualTo(MatchFactorStatus.NOT_MATCHED));
        assertThat(factor(match, MatchFactorKey.INTENTION).unmatched()).containsExactly("INVEST");
        assertThat(factor(match, MatchFactorKey.LOCATION).reason())
                .isEqualTo(OpportunityMatcher.REASON_DIFFERENT_LOCATION);
    }

    @Test
    void matchesPreferredSectorsByIdOrByCaseInsensitiveSlug() {
        MatchOpportunity opportunity = opportunity();

        MatchFactor byId = factor(OpportunityMatcher.match(
                profileWithSectors(Set.of(SECTOR_ID.toString())), opportunity), MatchFactorKey.SECTOR);
        MatchFactor bySlug = factor(OpportunityMatcher.match(
                profileWithSectors(Set.of("Sostenibilidad")), opportunity), MatchFactorKey.SECTOR);

        assertThat(byId.status()).isEqualTo(MatchFactorStatus.MATCHED);
        assertThat(bySlug.status()).isEqualTo(MatchFactorStatus.MATCHED);
    }

    @Test
    void ignoresFactorsTheOpportunityDoesNotStateAndScoresTheRest() {
        MatchOpportunity bare = new MatchOpportunity(
                UUID.randomUUID(), "Abierta", "OTHER", null, null, false, List.of(), SECTOR, "MVP", List.of());
        MatchProfile profile = new MatchProfile(
                true, Set.of("WORK"), Set.of("sostenibilidad"), Set.of("MVP"), Set.of(), Set.of(), null, null);

        OpportunityMatch match = OpportunityMatcher.match(profile, bare);

        assertThat(factor(match, MatchFactorKey.SKILLS).status()).isEqualTo(MatchFactorStatus.NOT_APPLICABLE);
        assertThat(factor(match, MatchFactorKey.INTENTION).status()).isEqualTo(MatchFactorStatus.NOT_APPLICABLE);
        assertThat(factor(match, MatchFactorKey.INTERESTS).status()).isEqualTo(MatchFactorStatus.NOT_APPLICABLE);
        assertThat(factor(match, MatchFactorKey.LOCATION).status()).isEqualTo(MatchFactorStatus.NOT_APPLICABLE);
        assertThat(factor(match, MatchFactorKey.SKILLS).weight()).isZero();
        assertThat(match.score()).isEqualTo(100);
    }

    @Test
    void scoresInterestsProportionallyToTheSmallerOfBothSets() {
        MatchOpportunity opportunity = opportunity();
        MatchProfile profile = new MatchProfile(
                true, Set.of(), Set.of(), Set.of(), Set.of(IMPACT_TAG_ID, AGRO_TAG_ID), Set.of(), null, null);

        MatchFactor interests = factor(OpportunityMatcher.match(profile, opportunity), MatchFactorKey.INTERESTS);

        assertThat(interests.status()).isEqualTo(MatchFactorStatus.MATCHED);
        assertThat(interests.matched()).containsExactly("Impacto");
    }

    @Test
    void treatsRemoteOpportunitiesAsLocationCompatibleEvenWithoutAProfileLocation() {
        MatchOpportunity remote = new MatchOpportunity(
                UUID.randomUUID(), "Remota", "TALENT", "La Paz", "La Paz", true,
                List.of(), SECTOR, "MVP", List.of());
        MatchProfile noLocation = new MatchProfile(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, null);

        MatchFactor location = factor(OpportunityMatcher.match(noLocation, remote), MatchFactorKey.LOCATION);

        assertThat(location.status()).isEqualTo(MatchFactorStatus.MATCHED);
        assertThat(location.reason()).isEqualTo(OpportunityMatcher.REASON_REMOTE_ALLOWED);
        assertThat(location.points()).isEqualTo(10);
    }

    @Test
    void comparesLocationsIgnoringCaseAndAccentsAndGivesHalfCreditForTheSameDepartment() {
        MatchOpportunity opportunity = new MatchOpportunity(
                UUID.randomUUID(), "Presencial", "TALENT", "Quillacollo", "Cochabamba", false,
                List.of(), SECTOR, "MVP", List.of());

        MatchFactor sameCity = location(opportunity, "quillacóllo", "Cochabamba");
        MatchFactor sameDepartment = location(opportunity, "Sacaba", "COCHABAMBA");

        assertThat(sameCity.reason()).isEqualTo(OpportunityMatcher.REASON_SAME_CITY);
        assertThat(sameCity.points()).isEqualTo(10);
        assertThat(sameDepartment.status()).isEqualTo(MatchFactorStatus.PARTIAL);
        assertThat(sameDepartment.reason()).isEqualTo(OpportunityMatcher.REASON_SAME_DEPARTMENT);
        assertThat(sameDepartment.points()).isEqualTo(5);
    }

    @Test
    void isDeterministicAndKeepsTheScoreWithinBounds() {
        MatchProfile profile = new MatchProfile(
                true, Set.of("WORK", "INVEST"), Set.of("sostenibilidad"), Set.of("MVP", "IDEA"),
                Set.of(IMPACT_TAG_ID), Set.of(JAVA_ID), "Cochabamba", null);

        OpportunityMatch first = OpportunityMatcher.match(profile, opportunity());
        OpportunityMatch second = OpportunityMatcher.match(profile, opportunity());

        assertThat(second).isEqualTo(first);
        assertThat(first.score()).isBetween(0, 100);
        assertThat(first.factors()).allSatisfy(factor ->
                assertThat(factor.points()).isBetween(0, factor.weight()));
    }

    private static MatchFactor location(MatchOpportunity opportunity, String city, String department) {
        MatchProfile profile = new MatchProfile(
                true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), city, department);
        return factor(OpportunityMatcher.match(profile, opportunity), MatchFactorKey.LOCATION);
    }

    private static MatchProfile profileWithSkills(Set<UUID> skills) {
        return new MatchProfile(true, Set.of(), Set.of(), Set.of(), Set.of(), skills, null, null);
    }

    private static MatchProfile profileWithSectors(Set<String> sectors) {
        return new MatchProfile(true, Set.of(), sectors, Set.of(), Set.of(), Set.of(), null, null);
    }

    private static MatchFactor factor(OpportunityMatch match, MatchFactorKey key) {
        return match.factors().stream().filter(factor -> factor.key() == key).findFirst().orElseThrow();
    }

    private static MatchOpportunity opportunity() {
        return new MatchOpportunity(
                UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                "Desarrollador backend",
                "TALENT",
                "Cochabamba",
                "Cochabamba",
                false,
                List.of(
                        new MatchOpportunity.Skill(FIGMA_ID, "Figma", false),
                        new MatchOpportunity.Skill(JAVA_ID, "Java", true)),
                SECTOR,
                "MVP",
                List.of(new MatchOpportunity.Tag(IMPACT_TAG_ID, "Impacto")));
    }
}
