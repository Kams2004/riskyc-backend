package com.fashion.Riskyc.dto.response;

import java.util.UUID;

public record SubcategoryResponse(
        UUID id,
        String slug,
        String name,
        /** Machine-translated French version — null until a translation has succeeded. */
        String nameFr,
        /** Storefront-visible product count — subcategories with none are hidden from the storefront (an admin still sees them, e.g. to assign products to). */
        long productCount
) {
}
