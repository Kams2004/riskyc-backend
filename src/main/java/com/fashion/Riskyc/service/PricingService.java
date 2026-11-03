package com.fashion.Riskyc.service;

import com.fashion.Riskyc.entity.BulkPriceTier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared bulk/tiered-pricing allocation — used by both the storefront
 * checkout ({@link OrderService}) and the POS till ({@link PosSaleService})
 * so a quantity discount applies identically regardless of which channel
 * rang up the sale. Extracted from OrderService, where this logic
 * originated; behavior is unchanged.
 */
@Service
public class PricingService {

    /**
     * Splits the bulk/grouped-pricing tiers for a product across a list of
     * lines requesting that same product (e.g. one line per photo the
     * per-photo picker was used to configure), pricing all of them together
     * as a single continuous quantity rather than each line in isolation —
     * 2 + 3 + 5 units of the same product across three lines gets a
     * 10-for-20,000 tier exactly as if they'd been one line of 10, instead
     * of each line individually falling short of the tier and paying full
     * price. A later order adding one more unit (making 11) correctly costs
     * the 10-unit tier plus one unit at the regular price, not a re-priced
     * 11-unit blend.
     *
     * Tiers apply greedily by largest-quantity first (mirrors lib/pricing.ts
     * on the frontend, which shows the customer this same figure before
     * they submit): as many of the biggest tier as fit, then the next, and
     * so on, with any leftover charged at the regular unit price — never a
     * partial/prorated tier rate. The resulting tier "chunks" are then
     * walked in the order the lines were given, splitting a chunk across a
     * line boundary when a line's quantity doesn't line up evenly with it,
     * so `quantities.size() == 1` naturally reduces to pricing one plain line.
     */
    public List<BigDecimal> allocateGroupedLineTotals(BigDecimal unitPrice, List<BulkPriceTier> bulkPrices, List<Integer> quantities) {
        int totalQuantity = quantities.stream().mapToInt(Integer::intValue).sum();
        if (totalQuantity <= 0) return quantities.stream().map(q -> BigDecimal.ZERO).toList();

        List<BulkPriceTier> tiers = bulkPrices.stream()
                .filter(t -> t.getQuantity() != null && t.getPrice() != null && t.getQuantity() > 0 && t.getPrice().signum() > 0)
                .sorted(Comparator.comparingInt(BulkPriceTier::getQuantity).reversed())
                .toList();

        record Chunk(int units, BigDecimal totalPrice) {}
        List<Chunk> chunks = new ArrayList<>();
        int remaining = totalQuantity;
        for (BulkPriceTier tier : tiers) {
            if (remaining >= tier.getQuantity()) {
                int count = remaining / tier.getQuantity();
                for (int i = 0; i < count; i++) {
                    chunks.add(new Chunk(tier.getQuantity(), tier.getPrice()));
                }
                remaining -= count * tier.getQuantity();
            }
        }
        if (remaining > 0) {
            chunks.add(new Chunk(remaining, unitPrice.multiply(BigDecimal.valueOf(remaining))));
        }

        List<BigDecimal> lineTotals = new ArrayList<>(quantities.size());
        int chunkIndex = 0;
        int unitsUsedInChunk = 0;
        for (int quantity : quantities) {
            BigDecimal lineTotal = BigDecimal.ZERO;
            int remainingForLine = quantity;
            while (remainingForLine > 0) {
                Chunk chunk = chunks.get(chunkIndex);
                int unitsLeftInChunk = chunk.units() - unitsUsedInChunk;
                int take = Math.min(remainingForLine, unitsLeftInChunk);
                BigDecimal perUnit = chunk.totalPrice().divide(BigDecimal.valueOf(chunk.units()), 4, RoundingMode.HALF_UP);
                lineTotal = lineTotal.add(perUnit.multiply(BigDecimal.valueOf(take)));
                unitsUsedInChunk += take;
                remainingForLine -= take;
                if (unitsUsedInChunk >= chunk.units()) {
                    chunkIndex++;
                    unitsUsedInChunk = 0;
                }
            }
            lineTotals.add(lineTotal.setScale(2, RoundingMode.HALF_UP));
        }
        return lineTotals;
    }
}
