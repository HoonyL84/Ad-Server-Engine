package io.hoony.adserver.domain.serving;

public enum ServingFallbackReason {
    NONE,
    PROFILE_NOT_FOUND,
    TARGET_NOT_MATCHED,
    DMP_TIMEOUT,
    DMP_ERROR,
    DMP_CIRCUIT_OPEN,
    CANDIDATE_TIMEOUT,
    CANDIDATE_ERROR,
    BUDGET_EXHAUSTED,
    NO_CANDIDATE
}
