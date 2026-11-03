package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.EnterpriseStatus;

/** Minimal, anonymous-safe status lookup for the "Become a Seller" wizard's returning applicant — not the full EnterpriseResponse, which requires VIEW_ENTERPRISES. */
public record EnterpriseStatusResponse(
        String name,
        EnterpriseStatus status
) {
}
