package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.response.PosCatalogResponse;
import com.fashion.Riskyc.dto.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Serves the POS catalog with an ETag so a till polling for changes downloads nothing when nothing changed.
 * Image links are presigned and re-signed on every request, so they're left out of the fingerprint — otherwise
 * it would change every time even with an identical catalog.
 */
@Component
@RequiredArgsConstructor
public class PosCatalogEtag {

    private static final Pattern SIGNED_URL = Pattern.compile("\"[A-Za-z]+\":\"[^\"]*X-Amz-[^\"]*\"");

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public ResponseEntity<PosCatalogResponse> respond(String ifNoneMatch) {
        List<ProductResponse> products = productService.listForPos();
        String etag = "\"" + PosDeviceService.sha256(SIGNED_URL.matcher(objectMapper.writeValueAsString(products)).replaceAll("")) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(new PosCatalogResponse(Instant.now(), products));
    }
}
