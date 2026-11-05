package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.request.DeviceHeartbeatRequest;
import com.fashion.Riskyc.dto.request.DeviceSaleBatchRequest;
import com.fashion.Riskyc.dto.request.EnrollDeviceRequest;
import com.fashion.Riskyc.dto.response.DeviceHeartbeatResponse;
import com.fashion.Riskyc.dto.response.DeviceSaleResultResponse;
import com.fashion.Riskyc.dto.response.EnrollDeviceResponse;
import com.fashion.Riskyc.dto.response.EnrollmentCodeResponse;
import com.fashion.Riskyc.dto.response.PosCatalogResponse;
import com.fashion.Riskyc.dto.response.PosDeviceResponse;
import com.fashion.Riskyc.dto.response.PosSettingsResponse;
import com.fashion.Riskyc.service.PosCatalogEtag;
import com.fashion.Riskyc.service.PosDeviceService;
import com.fashion.Riskyc.service.PosDeviceSyncService;
import com.fashion.Riskyc.service.PosSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Tills (the POS desktop app). Admin routes manage enrolment and revocation; /api/pos/device/** is called by an
 * enrolled till with its X-Device-Token, never with a cashier's login, so it keeps working while cashiers are
 * logged out or their sessions have expired.
 */
@RestController
@RequiredArgsConstructor
public class PosDeviceController {

    private final PosDeviceService posDeviceService;
    private final PosDeviceSyncService posDeviceSyncService;
    private final PosSettingsService posSettingsService;
    private final PosCatalogEtag posCatalogEtag;

    // ── Admin (MANAGE_POS) ──

    @PostMapping("/api/pos/devices/enrollment-codes")
    public EnrollmentCodeResponse createEnrollmentCode() {
        return posDeviceService.createEnrollmentCode();
    }

    @GetMapping("/api/pos/devices")
    public List<PosDeviceResponse> listDevices() {
        return posDeviceService.list();
    }

    @PostMapping("/api/pos/devices/{id}/revoke")
    public PosDeviceResponse revoke(@PathVariable UUID id) {
        return posDeviceService.revoke(id);
    }

    // ── Enrolment (no login — the one-time code is the credential) ──

    @PostMapping("/api/pos/devices/enroll")
    public EnrollDeviceResponse enroll(@Valid @RequestBody EnrollDeviceRequest request) {
        return posDeviceService.enroll(request);
    }

    // ── The till itself (X-Device-Token) ──

    @PostMapping("/api/pos/device/heartbeat")
    public DeviceHeartbeatResponse heartbeat(@Valid @RequestBody DeviceHeartbeatRequest request) {
        return posDeviceService.heartbeat(request);
    }

    /** Full catalog snapshot for the till's shop; 304 when the till's If-None-Match fingerprint is still current. */
    @GetMapping("/api/pos/device/catalog")
    public ResponseEntity<PosCatalogResponse> catalog(@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return posCatalogEtag.respond(ifNoneMatch);
    }

    @GetMapping("/api/pos/device/settings")
    public PosSettingsResponse settings() {
        return posSettingsService.get();
    }

    @PostMapping("/api/pos/device/sales/batch")
    public List<DeviceSaleResultResponse> uploadSales(@Valid @RequestBody DeviceSaleBatchRequest request) {
        return posDeviceSyncService.uploadSales(request.sales());
    }
}
