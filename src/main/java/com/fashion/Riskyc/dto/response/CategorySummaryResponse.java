package com.fashion.Riskyc.dto.response;

import java.util.UUID;

/** A lightweight id/name reference to a Category — used wherever only "which category" matters, not its full subcategory tree (e.g. an Enterprise's assigned/suggested categories). */
public record CategorySummaryResponse(
        UUID id,
        String slug,
        String name
) {
}
