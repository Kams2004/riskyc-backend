package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EnterpriseApplicationRequest(
        @NotBlank String businessName,
        String description,
        @NotBlank String businessType,
        @NotBlank String contactFirstName,
        @NotBlank String contactLastName,
        @NotBlank @Email String contactEmail,
        String contactPhone,
        /** Shown to customers as this seller's "Contact Supplier" WhatsApp link once approved. */
        String whatsappNumber,
        /** "What do you sell?" in the applicant's own words — one input to the suggested-category heuristic. */
        @NotBlank String whatDoYouSell,
        /** A rough self-reported catalog size, e.g. "1-10", "10-50", "50+" — free text, shown to the super admin only. */
        String estimatedCatalogSize
) {
}
