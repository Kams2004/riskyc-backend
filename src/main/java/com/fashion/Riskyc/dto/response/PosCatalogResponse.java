package com.fashion.Riskyc.dto.response;

import java.time.Instant;
import java.util.List;

/** fetchedAt lets the POS client show "catalog as of HH:MM" and warn if it's gone stale while still offline. */
public record PosCatalogResponse(Instant fetchedAt, List<ProductResponse> products) {
}
