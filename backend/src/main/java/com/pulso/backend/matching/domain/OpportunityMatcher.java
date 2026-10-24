package com.pulso.backend.matching.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic, explainable compatibility between a person and one opportunity.
 *
 * <p>Each factor yields a ratio between 0 and 1. A factor the opportunity does not
 * state anything about is ignored; a factor the person has not provided data for
 * stays in the score with no credit and is reported as missing profile data. The
 * score is the weighted ratio over the applicable factors, from 0 to 100.
 */
public final class OpportunityMatcher {

    public static final String REASON_REMOTE_ALLOWED = "REMOTE_ALLOWED";
    public static final String REASON_SAME_CITY = "SAME_CITY";
    public static final String REASON_SAME_DEPARTMENT = "SAME_DEPARTMENT";
    public static final String REASON_DIFFERENT_LOCATION = "DIFFERENT_LOCATION";

    private static final int REQUIRED_SKILL_WEIGHT = 2;
    private static final int PREFERRED_SKILL_WEIGHT = 1;
    private static final double SAME_DEPARTMENT_RATIO = 0.5;

    /** Opportunity types each onboarding intention is compatible with. */
    private static final Map<String, Set<String>> INTENTION_TYPES = Map.of(
            "WORK", Set.of("TALENT"),
            "COLLABORATE", Set.of("COLLABORATION", "PARTNERSHIP", "MENTOR"),
            "INVEST", Set.of("INVESTMENT"),
            "BUY", Set.of("CUSTOMER", "BETA_TESTER"),
            "FIND_PARTNERS", Set.of("PARTNERSHIP", "COLLABORATION"),
            "FIND_CUSTOMERS", Set.of("SUPPLIER"),
            "FIND_PROVIDERS", Set.of("CUSTOMER", "BETA_TESTER"));

    private OpportunityMatcher() {
    }

    public static OpportunityMatch match(MatchProfile profile, MatchOpportunity opportunity) {
        List<Scored> scored = List.of(
                skills(profile, opportunity),
                intention(profile, opportunity),
                interests(profile, opportunity),
                sector(profile, opportunity),
                stage(profile, opportunity),
                location(profile, opportunity));

        List<MatchFactor> factors = scored.stream().map(Scored::toFactor).toList();
        int applicableWeight = scored.stream().mapToInt(Scored::weight).sum();
        double earned = scored.stream().mapToDouble(item -> item.weight() * item.ratio()).sum();
        int score = applicableWeight == 0 ? 0 : (int) Math.round(100 * earned / applicableWeight);

        return new OpportunityMatch(
                opportunity.id(),
                opportunity.title(),
                opportunity.opportunityType(),
                score,
                profile.onboardingCompleted(),
                factors);
    }

    private static Scored skills(MatchProfile profile, MatchOpportunity opportunity) {
        List<MatchOpportunity.Skill> skills = opportunity.skills().stream()
                .sorted(Comparator
                        .comparing((MatchOpportunity.Skill skill) -> !skill.required())
                        .thenComparing(skill -> skill.name().toLowerCase(Locale.ROOT))
                        .thenComparing(MatchOpportunity.Skill::id))
                .toList();
        if (skills.isEmpty()) {
            return Scored.notApplicable(MatchFactorKey.SKILLS);
        }
        if (profile.skillIds().isEmpty()) {
            return Scored.missing(MatchFactorKey.SKILLS, names(skills));
        }

        List<String> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        int total = 0;
        int earned = 0;
        for (MatchOpportunity.Skill skill : skills) {
            int weight = skill.required() ? REQUIRED_SKILL_WEIGHT : PREFERRED_SKILL_WEIGHT;
            total += weight;
            if (profile.skillIds().contains(skill.id())) {
                earned += weight;
                matched.add(skill.name());
            } else {
                unmatched.add(skill.name());
            }
        }
        return Scored.of(MatchFactorKey.SKILLS, (double) earned / total, matched, unmatched, null);
    }

    private static Scored intention(MatchProfile profile, MatchOpportunity opportunity) {
        String type = opportunity.opportunityType();
        if (type == null || type.equals("OTHER")) {
            return Scored.notApplicable(MatchFactorKey.INTENTION);
        }
        if (profile.intentions().isEmpty()) {
            return Scored.missing(MatchFactorKey.INTENTION, List.of());
        }

        List<String> matched = profile.intentions().stream()
                .filter(intention -> INTENTION_TYPES.getOrDefault(intention, Set.of()).contains(type))
                .sorted()
                .toList();
        if (!matched.isEmpty()) {
            return Scored.of(MatchFactorKey.INTENTION, 1, matched, List.of(), null);
        }
        return Scored.of(MatchFactorKey.INTENTION, 0, List.of(), profile.intentions().stream().sorted().toList(), null);
    }

    private static Scored interests(MatchProfile profile, MatchOpportunity opportunity) {
        List<MatchOpportunity.Tag> tags = opportunity.tags().stream()
                .sorted(Comparator
                        .comparing((MatchOpportunity.Tag tag) -> tag.name().toLowerCase(Locale.ROOT))
                        .thenComparing(MatchOpportunity.Tag::id))
                .toList();
        if (tags.isEmpty()) {
            return Scored.notApplicable(MatchFactorKey.INTERESTS);
        }
        if (profile.tagIds().isEmpty()) {
            return Scored.missing(MatchFactorKey.INTERESTS, tags.stream().map(MatchOpportunity.Tag::name).toList());
        }

        List<String> matched = tags.stream()
                .filter(tag -> profile.tagIds().contains(tag.id()))
                .map(MatchOpportunity.Tag::name)
                .toList();
        List<String> unmatched = tags.stream()
                .filter(tag -> !profile.tagIds().contains(tag.id()))
                .map(MatchOpportunity.Tag::name)
                .toList();
        double ratio = Math.min(1.0, (double) matched.size() / Math.min(profile.tagIds().size(), tags.size()));
        return Scored.of(MatchFactorKey.INTERESTS, ratio, matched, unmatched, null);
    }

    private static Scored sector(MatchProfile profile, MatchOpportunity opportunity) {
        MatchOpportunity.Sector sector = opportunity.sector();
        if (sector == null) {
            return Scored.notApplicable(MatchFactorKey.SECTOR);
        }
        if (profile.preferredSectors().isEmpty()) {
            return Scored.missing(MatchFactorKey.SECTOR, List.of(sector.name()));
        }

        Set<String> preferred = profile.preferredSectors().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        boolean matches = (sector.id() != null && preferred.contains(sector.id().toString()))
                || (sector.slug() != null && preferred.contains(sector.slug().toLowerCase(Locale.ROOT)));
        return matches
                ? Scored.of(MatchFactorKey.SECTOR, 1, List.of(sector.name()), List.of(), null)
                : Scored.of(MatchFactorKey.SECTOR, 0, List.of(), List.of(sector.name()), null);
    }

    private static Scored stage(MatchProfile profile, MatchOpportunity opportunity) {
        String stage = opportunity.stage();
        if (stage == null || stage.isBlank()) {
            return Scored.notApplicable(MatchFactorKey.STAGE);
        }
        if (profile.preferredStages().isEmpty()) {
            return Scored.missing(MatchFactorKey.STAGE, List.of(stage));
        }
        return profile.preferredStages().contains(stage)
                ? Scored.of(MatchFactorKey.STAGE, 1, List.of(stage), List.of(), null)
                : Scored.of(MatchFactorKey.STAGE, 0, List.of(), List.of(stage), null);
    }

    private static Scored location(MatchProfile profile, MatchOpportunity opportunity) {
        if (opportunity.remoteAllowed()) {
            return Scored.of(MatchFactorKey.LOCATION, 1, List.of(), List.of(), REASON_REMOTE_ALLOWED);
        }
        String city = normalize(opportunity.city());
        String department = normalize(opportunity.department());
        if (city == null && department == null) {
            return Scored.notApplicable(MatchFactorKey.LOCATION);
        }

        String label = label(opportunity.city(), opportunity.department());
        String profileCity = normalize(profile.city());
        String profileDepartment = normalize(profile.department());
        if (profileCity == null && profileDepartment == null) {
            return Scored.missing(MatchFactorKey.LOCATION, List.of(label));
        }

        boolean sameDepartment = department != null && department.equals(profileDepartment);
        if (city != null && city.equals(profileCity)) {
            return Scored.of(MatchFactorKey.LOCATION, 1, List.of(label), List.of(), REASON_SAME_CITY);
        }
        if (sameDepartment) {
            double ratio = city == null ? 1 : SAME_DEPARTMENT_RATIO;
            return Scored.of(MatchFactorKey.LOCATION, ratio, List.of(label), List.of(), REASON_SAME_DEPARTMENT);
        }
        return Scored.of(MatchFactorKey.LOCATION, 0, List.of(), List.of(label), REASON_DIFFERENT_LOCATION);
    }

    private static List<String> names(List<MatchOpportunity.Skill> skills) {
        return skills.stream().map(MatchOpportunity.Skill::name).toList();
    }

    private static String label(String city, String department) {
        List<String> parts = new ArrayList<>();
        if (city != null && !city.isBlank()) {
            parts.add(city.trim());
        }
        if (department != null && !department.isBlank()) {
            parts.add(department.trim());
        }
        return String.join(", ", parts);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private record Scored(
            MatchFactorKey key,
            MatchFactorStatus status,
            int weight,
            double ratio,
            List<String> matched,
            List<String> unmatched,
            String reason) {

        static Scored of(
                MatchFactorKey key,
                double ratio,
                List<String> matched,
                List<String> unmatched,
                String reason) {
            MatchFactorStatus status = ratio >= 1
                    ? MatchFactorStatus.MATCHED
                    : ratio > 0 ? MatchFactorStatus.PARTIAL : MatchFactorStatus.NOT_MATCHED;
            return new Scored(key, status, key.weight(), ratio, matched, unmatched, reason);
        }

        static Scored missing(MatchFactorKey key, List<String> expected) {
            return new Scored(key, MatchFactorStatus.MISSING_PROFILE_DATA, key.weight(), 0, List.of(), expected, null);
        }

        static Scored notApplicable(MatchFactorKey key) {
            return new Scored(key, MatchFactorStatus.NOT_APPLICABLE, 0, 0, List.of(), List.of(), null);
        }

        MatchFactor toFactor() {
            return new MatchFactor(key, status, weight, (int) Math.round(weight * ratio), matched, unmatched, reason);
        }
    }
}
