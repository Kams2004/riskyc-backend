package com.fashion.Riskyc.dto.response;

/**
 * The customer-safe subset of a product's owning Enterprise — just enough
 * for a storefront "Contact Supplier" action. Deliberately excludes
 * contactEmail/contactFirstName/contactLastName/estimatedCatalogSize etc.,
 * which stay admin-only on EnterpriseResponse.
 */
public record SellerSummaryResponse(
        String name,
        String phone,
        String whatsappNumber,
        /** False until the shop has set up its own payment numbers — its products can't be bought online yet. */
        boolean acceptsPayments
) {
}
