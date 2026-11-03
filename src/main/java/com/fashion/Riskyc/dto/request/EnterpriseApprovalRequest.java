package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** The super admin's confirmed (or overridden) choice of which top-level Categories this enterprise may list products under — the suggested list is a starting point, never auto-applied. */
public record EnterpriseApprovalRequest(
        @NotEmpty List<UUID> categoryIds
) {
}
