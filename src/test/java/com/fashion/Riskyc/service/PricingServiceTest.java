package com.fashion.Riskyc.service;

import com.fashion.Riskyc.entity.BulkPriceTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Codifies the tiered-pricing algorithm shared by OrderService (storefront
 * checkout) and PosSaleService (the till) — see PricingService's own
 * javadoc for the allocation rules this exercises.
 */
class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    private static BulkPriceTier tier(int quantity, String price) {
        return new BulkPriceTier(quantity, new BigDecimal(price));
    }

    @Test
    void noTiersDefined_fallsBackToFlatPricing() {
        List<BigDecimal> totals = pricingService.allocateGroupedLineTotals(
                new BigDecimal("3000"), List.of(), List.of(5));

        assertThat(totals).containsExactly(new BigDecimal("15000.00"));
    }

    @Test
    void exactTierMultiple_appliesTheTierCleanly() {
        List<BulkPriceTier> tiers = List.of(tier(10, "20000"));

        List<BigDecimal> totals = pricingService.allocateGroupedLineTotals(
                new BigDecimal("3000"), tiers, List.of(10));

        assertThat(totals).containsExactly(new BigDecimal("20000.00"));
    }

    @Test
    void remainderAboveATier_chargesTheLeftoverAtFlatPrice() {
        List<BulkPriceTier> tiers = List.of(tier(10, "20000"));

        // 12 units: one 10-tier (20,000) + 2 at the regular 3,000 price = 26,000
        List<BigDecimal> totals = pricingService.allocateGroupedLineTotals(
                new BigDecimal("3000"), tiers, List.of(12));

        assertThat(totals).containsExactly(new BigDecimal("26000.00"));
    }

    @Test
    void multipleTiers_appliesLargestFirst() {
        List<BulkPriceTier> tiers = List.of(tier(5, "9000"), tier(10, "16000"));

        // 15 units: one 10-tier (16,000) + one 5-tier (9,000) = 25,000
        List<BigDecimal> totals = pricingService.allocateGroupedLineTotals(
                new BigDecimal("2000"), tiers, List.of(15));

        assertThat(totals).containsExactly(new BigDecimal("25000.00"));
    }

    @Test
    void groupedAcrossSeveralLines_poolsTheCombinedQuantity() {
        List<BulkPriceTier> tiers = List.of(tier(10, "20000"));

        // 2 + 3 + 5 units across three lines of the same product = 10 total,
        // so the whole group gets the tier, split back proportionally.
        List<BigDecimal> totals = pricingService.allocateGroupedLineTotals(
                new BigDecimal("3000"), tiers, List.of(2, 3, 5));

        assertThat(totals).hasSize(3);
        BigDecimal sum = totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("20000.00");
        assertThat(totals.get(0)).isEqualByComparingTo("4000.00"); // 2/10 of 20,000
        assertThat(totals.get(1)).isEqualByComparingTo("6000.00"); // 3/10 of 20,000
        assertThat(totals.get(2)).isEqualByComparingTo("10000.00"); // 5/10 of 20,000
    }

    @Test
    void zeroOrNegativeQuantity_pricesEverythingAtZero() {
        List<BigDecimal> totals = pricingService.allocateGroupedLineTotals(
                new BigDecimal("3000"), List.of(), List.of(0, 0));

        assertThat(totals).containsExactly(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void invalidTiers_areIgnored() {
        // A zero/negative quantity or price tier should never be selected.
        List<BulkPriceTier> tiers = List.of(tier(0, "100"), tier(5, "0"), tier(-2, "500"));

        List<BigDecimal> totals = pricingService.allocateGroupedLineTotals(
                new BigDecimal("1000"), tiers, List.of(3));

        assertThat(totals).containsExactly(new BigDecimal("3000.00"));
    }
}
