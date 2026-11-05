package com.fashion.Riskyc.service;

import com.fashion.Riskyc.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Every 15 minutes, releases the stock of orders that were never paid within {@code app.orders.unpaid-expiry-hours}
 * (24 by default) — see {@link OrderService#expireIfUnpaid(UUID)}. Each order expires in its own transaction, so
 * one failure doesn't block the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidOrderExpiryJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${app.orders.unpaid-expiry-hours:24}")
    private long expiryHours;

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    public void expireUnpaidOrders() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(expiryHours));
        int expired = 0;
        for (UUID id : orderRepository.findUnpaidIdsCreatedBefore(OrderService.UNPAID_STATUSES, cutoff)) {
            try {
                if (orderService.expireIfUnpaid(id)) expired++;
            } catch (RuntimeException e) {
                log.warn("Couldn't expire unpaid order {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) log.info("Expired {} unpaid order(s) older than {}h and returned their stock", expired, expiryHours);
    }
}
