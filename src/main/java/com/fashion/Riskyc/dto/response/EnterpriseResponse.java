package com.fashion.Riskyc.dto.response;

import com.fashion.Riskyc.entity.EnterpriseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EnterpriseResponse(
        UUID id,
        String name,
        String description,
        String businessType,
        EnterpriseStatus status,
        String contactFirstName,
        String contactLastName,
        String contactEmail,
        String contactPhone,
        String whatDoYouSell,
        String estimatedCatalogSize,
        List<CategorySummaryResponse> categories,
        /** A starting-point suggestion the super admin confirms or overrides at approval time — never persisted, computed on each read. */
        List<CategorySummaryResponse> suggestedCategories,
        String approvedByName,
        Instant approvedAt,
        Instant createdAt
) {
}
