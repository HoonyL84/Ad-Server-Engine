package io.hoony.adserver.domain.serving;

public enum ServingFallbackReason {
    NONE,
    PROFILE_NOT_FOUND,
    TARGET_NOT_MATCHED,
    DMP_TIMEOUT,
    DMP_ERROR,
    CANDIDATE_TIMEOUT,
    CANDIDATE_ERROR,
    NO_CANDIDATE
}
