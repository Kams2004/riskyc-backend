package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.CreatePosSaleRequest;
import com.fashion.Riskyc.dto.response.PosCatalogResponse;
import com.fashion.Riskyc.dto.response.PosSaleResponse;
import com.fashion.Riskyc.service.PosSaleService;
import com.fashion.Riskyc.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosSaleService posSaleService;
    private final ProductService productService;

    /** What a POS terminal caches locally to browse/sell offline. */
    @GetMapping("/catalog")
    public PosCatalogResponse catalog() {
        return new PosCatalogResponse(Instant.now(), productService.listForPos());
    }

    @GetMapping("/sales")
    public List<PosSaleResponse> listSales() {
        return posSaleService.listAll();
    }

    /** Sales whose stock decrement went negative — needs admin reconciliation. */
    @GetMapping("/sales/flagged")
    public List<PosSaleResponse> listFlaggedSales() {
        return posSaleService.listFlagged();
    }

    /** Idempotent by clientSaleId — see CreatePosSaleRequest's own javadoc. Always 200: a retried sync returns the already-persisted sale exactly as if it had just been created, so the client has no reason to branch on which happened. */
    @PostMapping("/sales")
    public PosSaleResponse createSale(@Valid @RequestBody CreatePosSaleRequest request) {
        return posSaleService.create(request);
    }

    @PostMapping(value = "/sales/{id}/receipt", consumes = "multipart/form-data")
    public PosSaleResponse uploadReceipt(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return posSaleService.uploadReceipt(id, file);
    }

    @PostMapping("/sales/{id}/void")
    public PosSaleResponse voidSale(@PathVariable UUID id) {
        return posSaleService.voidSale(id);
    }
}
