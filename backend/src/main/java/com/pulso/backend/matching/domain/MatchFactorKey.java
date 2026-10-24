package com.pulso.backend.matching.domain;

public enum MatchFactorKey {
    SKILLS(35),
    INTENTION(20),
    INTERESTS(15),
    SECTOR(10),
    STAGE(10),
    LOCATION(10);

    private final int weight;

    MatchFactorKey(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
