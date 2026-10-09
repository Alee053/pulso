package com.pulso.backend.preference.domain;

import java.util.List;

public final class OnboardingOptions {

    public static final List<String> INTENTIONS = List.of(
            "DISCOVER", "BUY", "COLLABORATE", "WORK", "INVEST",
            "FIND_PARTNERS", "FIND_CUSTOMERS", "FIND_PROVIDERS", "EXPLORE_ECOSYSTEM");
    public static final List<String> STAGES = List.of(
            "IDEA", "PROTOTYPE", "MVP", "EARLY_STAGE", "GROWING", "ESTABLISHED");
    public static final List<String> PROFICIENCIES = List.of(
            "BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT");

    private OnboardingOptions() {
    }
}
