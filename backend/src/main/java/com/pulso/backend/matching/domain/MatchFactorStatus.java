package com.pulso.backend.matching.domain;

public enum MatchFactorStatus {
    /** Every compared item coincides. */
    MATCHED,
    /** Some, but not all, compared items coincide. */
    PARTIAL,
    /** The person provided data and none of it coincides. */
    NOT_MATCHED,
    /** The person has not provided the data needed to compare this factor. */
    MISSING_PROFILE_DATA,
    /** The opportunity does not state anything to compare, so the factor is ignored. */
    NOT_APPLICABLE
}
