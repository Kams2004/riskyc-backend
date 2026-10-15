package com.fashion.Riskyc.dto.request;

/** Both fields optional — only a caption edit, only an active toggle, or both at once. */
public record UpdatePublicityItemRequest(String caption, Boolean active) {
}
