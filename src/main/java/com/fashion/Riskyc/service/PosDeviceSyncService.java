package com.fashion.Riskyc.service;

import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.dto.request.DeviceSaleRequest;
import com.fashion.Riskyc.dto.response.DeviceSaleResultResponse;
import com.fashion.Riskyc.dto.response.PosSaleResponse;
import com.fashion.Riskyc.repository.PosSaleRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import com.fashion.Riskyc.security.DevicePrincipal;
import com.fashion.Riskyc.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uploads a till's offline sale queue. Deliberately NOT @Transactional: each sale goes through
 * PosSaleService.create in its own transaction, so one bad sale never rolls back the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosDeviceSyncService {

    private final PosDeviceService posDeviceService;
    private final PosSaleService posSaleService;
    private final PosSaleRepository posSaleRepository;

    public List<DeviceSaleResultResponse> uploadSales(List<DeviceSaleRequest> sales) {
        Authentication deviceAuth = SecurityContextHolder.getContext().getAuthentication();
        if (deviceAuth == null || !(deviceAuth.getPrincipal() instanceof DevicePrincipal device)) {
            throw new IllegalStateException("uploadSales requires an authenticated till");
        }
        UUID deviceShop = CurrentAdmin.enterpriseIdOrNull();

        List<DeviceSaleRequest> ordered = new ArrayList<>(sales);
        ordered.sort(Comparator.comparing(DeviceSaleRequest::soldAt));
        List<DeviceSaleResultResponse> results = new ArrayList<>(ordered.size());
        for (DeviceSaleRequest sale : ordered) {
            var existing = posSaleRepository.findByClientSaleId(sale.clientSaleId());
            if (existing.isPresent()) {
                results.add(new DeviceSaleResultResponse(sale.clientSaleId(), "DUPLICATE", existing.get().getId(), null));
                continue;
            }
            var cashier = posDeviceService.resolveCashier(sale.cashierId()).orElse(null);
            if (cashier == null || !Objects.equals(cashier.shopId(), deviceShop)) {
                results.add(new DeviceSaleResultResponse(sale.clientSaleId(), "REJECTED", null, "This cashier doesn't belong to this till's shop."));
                continue;
            }
            try {
                // Recorded exactly as if the cashier had rung it up online: same pricing, stock and shop rules.
                // The till checked the cashier's login when the sale was made, so it's accepted even if their
                // account or role has changed since — it's a sale that already happened.
                List<GrantedAuthority> authorities = cashier.principal().permissions().stream()
                        .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(JwtAuthenticationFilter.PERMISSION_AUTHORITY_PREFIX + p.name()))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(cashier.principal(), null, authorities));
                PosSaleResponse recorded = posSaleService.create(sale.toCreateRequest());
                results.add(new DeviceSaleResultResponse(sale.clientSaleId(), "RECORDED", recorded.id(), null));
            } catch (BadRequestException | ConflictException | ResourceNotFoundException | ForbiddenException | IllegalArgumentException e) {
                // The sale itself can't be recorded as sent (unknown product, bad data…) — retrying won't change that.
                log.warn("Till {} sale {} rejected: {}", device.deviceId(), sale.clientSaleId(), e.getMessage());
                results.add(new DeviceSaleResultResponse(sale.clientSaleId(), "REJECTED", null, e.getMessage()));
            } catch (RuntimeException e) {
                // A server-side hiccup (database, lock timeout…) — the till keeps the sale queued and tries again.
                log.warn("Till {} sale {} not recorded yet, will be retried: {}", device.deviceId(), sale.clientSaleId(), e.toString());
                results.add(new DeviceSaleResultResponse(sale.clientSaleId(), "ERROR", null, "The server couldn't record this sale right now; it will be retried."));
            } finally {
                SecurityContextHolder.getContext().setAuthentication(deviceAuth);
            }
        }
        return results;
    }
}
