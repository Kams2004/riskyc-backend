package com.fashion.Riskyc.entity;

public enum EnterpriseStatus {
    /** Application submitted, awaiting super admin review. */
    PENDING,
    /** Approved and trading — its admins can log in, its products are sellable. */
    ACTIVE,
    /** Temporarily blocked by a super admin — login and storefront visibility both stop; individual product hidden flags are untouched so they resume exactly as they were. */
    SUSPENDED,
    /** Application declined. */
    REJECTED
}
