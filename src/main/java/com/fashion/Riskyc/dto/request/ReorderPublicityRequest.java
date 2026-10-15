package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** The full, desired display order — every existing item's id, in the order they should appear. */
public record ReorderPublicityRequest(@NotEmpty List<UUID> orderedIds) {
}
