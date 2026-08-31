package com.fashion.Riskyc.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * A customer's referral dashboard: their own code/acronym, who referred them
 * (if anyone), and a two-level view of their referral chain — the people
 * they referred directly, and how many people each of those in turn
 * referred. Deeper levels (referrals of referrals of referrals) are
 * intentionally not surfaced.
 */
public record ReferralSummaryResponse(
        String referralCode,
        String acronym,
        String referredByAcronym,
        long directReferralCount,
        long indirectReferralCount,
        List<ReferralEntry> referrals
) {
    public record ReferralEntry(
            String acronym,
            Instant joinedAt,
            long referredCount
    ) {
    }
}
