package com.fashion.Riskyc.dto.request;

/** All fields optional — only the ones present are applied. */
public record UpdatePublicityItemRequest(String caption, Boolean active, String captionFont, String captionStyle) {
}
