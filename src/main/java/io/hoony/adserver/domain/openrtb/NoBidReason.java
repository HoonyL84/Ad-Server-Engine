package io.hoony.adserver.domain.openrtb;

/**
 * OpenRTB 입찰 미응답(No-Bid) 원인 분류.
 */
public enum NoBidReason {
    NONE,
    INVALID_REQUEST,
    SLOT_UNMATCHED,
    NO_CANDIDATE,
    BELOW_FLOOR,
    DMP_TIMEOUT,
    DMP_ERROR,
    DMP_CIRCUIT_OPEN,
    PROFILE_NOT_FOUND,
    TARGET_NOT_MATCHED,
    CANDIDATE_TIMEOUT,
    CANDIDATE_ERROR,
    SLA_TIMEOUT,
    BUDGET_EXHAUSTED
}
