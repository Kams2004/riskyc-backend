package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.DeviceHeartbeatRequest;
import com.fashion.Riskyc.dto.request.EnrollDeviceRequest;
import com.fashion.Riskyc.dto.response.DeviceHeartbeatResponse;
import com.fashion.Riskyc.dto.response.EnrollDeviceResponse;
import com.fashion.Riskyc.dto.response.EnrollmentCodeResponse;
import com.fashion.Riskyc.dto.response.PosDeviceResponse;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.EnterpriseStatus;
import com.fashion.Riskyc.entity.PosDevice;
import com.fashion.Riskyc.entity.PosDeviceEnrollment;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ConflictException;
import com.fashion.Riskyc.exception.ForbiddenException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.PosDeviceEnrollmentRepository;
import com.fashion.Riskyc.repository.PosDeviceRepository;
import com.fashion.Riskyc.security.AdminPrincipal;
import com.fashion.Riskyc.security.CurrentAdmin;
import com.fashion.Riskyc.security.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Till enrolment, authentication, check-ins and revocation — see PosDevice. */
@Service
@RequiredArgsConstructor
@Transactional
public class PosDeviceService {

    /** A till that hasn't checked in for this long is shown as offline (it checks in every ~15s while running). */
    static final Duration ONLINE_WINDOW = Duration.ofSeconds(60);
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    // No 0/O/1/I/L — the code is read off one screen and typed into another.
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PosDeviceRepository deviceRepository;
    private final PosDeviceEnrollmentRepository enrollmentRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final AdminUserRepository adminUserRepository;

    /** Admin side: a one-time code that enrols one till under the caller's shop. */
    public EnrollmentCodeResponse createEnrollmentCode() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        Enterprise enterprise = scopeId != null
                ? enterpriseRepository.findById(scopeId).orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId))
                : null;
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 8; i++) raw.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        String code = raw.substring(0, 4) + "-" + raw.substring(4);
        Instant expiresAt = Instant.now().plus(CODE_TTL);
        enrollmentRepository.save(PosDeviceEnrollment.builder()
                .codeHash(sha256(normalizeCode(code)))
                .enterprise(enterprise)
                .createdByName(CurrentAdmin.nameOrNull())
                .expiresAt(expiresAt)
                .build());
        return new EnrollmentCodeResponse(code, expiresAt);
    }

    /** Device side (no login): trades a valid code for a permanent device token, shown only this once. */
    public EnrollDeviceResponse enroll(EnrollDeviceRequest request) {
        PosDeviceEnrollment enrollment = enrollmentRepository.findByCodeHash(sha256(normalizeCode(request.code())))
                .orElseThrow(() -> new BadRequestException("This code isn't valid. Ask an admin for a new one."));
        if (enrollment.getUsedAt() != null) throw new BadRequestException("This code has already been used. Ask an admin for a new one.");
        if (enrollment.getExpiresAt().isBefore(Instant.now())) throw new BadRequestException("This code has expired. Ask an admin for a new one.");
        Enterprise enterprise = enrollment.getEnterprise();
        if (enterprise != null && enterprise.getStatus() != EnterpriseStatus.ACTIVE) {
            throw new BadRequestException("This shop is not active on the platform.");
        }
        enrollment.setUsedAt(Instant.now());

        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        PosDevice device = deviceRepository.save(PosDevice.builder()
                .enterprise(enterprise)
                .name(request.name().trim())
                .platform(request.platform())
                .appVersion(request.appVersion())
                .tokenHash(sha256(secret))
                .enrolledByName(enrollment.getCreatedByName())
                .enrolledAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build());
        return new EnrollDeviceResponse(device.getId(), device.getId() + "." + secret,
                enterprise != null ? enterprise.getId() : null,
                enterprise != null ? enterprise.getName() : "Riskyc Fashion");
    }

    /** Resolves an X-Device-Token header; empty for unknown, revoked, malformed, or suspended-shop devices. */
    @Transactional(readOnly = true)
    public Optional<DevicePrincipal> authenticate(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0) return Optional.empty();
        UUID id;
        try {
            id = UUID.fromString(token.substring(0, dot));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String secret = token.substring(dot + 1);
        return deviceRepository.findById(id)
                .filter(d -> d.getRevokedAt() == null)
                .filter(d -> MessageDigest.isEqual(d.getTokenHash().getBytes(StandardCharsets.US_ASCII), sha256(secret).getBytes(StandardCharsets.US_ASCII)))
                .filter(d -> d.getEnterprise() == null || d.getEnterprise().getStatus() == EnterpriseStatus.ACTIVE)
                .map(d -> new DevicePrincipal(d.getId().toString(), d.getName(),
                        d.getEnterprise() != null ? d.getEnterprise().getId().toString() : null));
    }

    public DeviceHeartbeatResponse heartbeat(DeviceHeartbeatRequest request) {
        PosDevice device = currentDevice();
        device.setLastSeenAt(Instant.now());
        if (request.appVersion() != null) device.setAppVersion(request.appVersion());
        if (request.pendingSales() != null) device.setPendingSales(request.pendingSales());
        return new DeviceHeartbeatResponse(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PosDeviceResponse> list() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        List<PosDevice> devices = scopeId != null
                ? deviceRepository.findByEnterpriseIdOrderByEnrolledAtDesc(scopeId)
                : deviceRepository.findByEnterpriseIsNullOrderByEnrolledAtDesc();
        return devices.stream().map(this::toResponse).toList();
    }

    public PosDeviceResponse revoke(UUID id) {
        PosDevice device = deviceRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Till", id));
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        UUID deviceScope = device.getEnterprise() != null ? device.getEnterprise().getId() : null;
        if (scopeId != null ? !scopeId.equals(deviceScope) : deviceScope != null) {
            throw new ForbiddenException("This till belongs to a different shop.");
        }
        if (device.getRevokedAt() != null) throw new ConflictException("This till is already revoked.");
        device.setRevokedAt(Instant.now());
        device.setRevokedByName(CurrentAdmin.nameOrNull());
        return toResponse(device);
    }

    /** A cashier as a ready-to-use principal, with their shop — loaded in a transaction so role/shop are initialised. */
    public record Cashier(UUID shopId, AdminPrincipal principal) {
    }

    @Transactional(readOnly = true)
    public Optional<Cashier> resolveCashier(UUID cashierId) {
        return adminUserRepository.findById(cashierId).map(u -> {
            UUID shopId = u.getEnterprise() != null ? u.getEnterprise().getId() : null;
            return new Cashier(shopId, new AdminPrincipal(
                    u.getId().toString(), u.getEmail(), (u.getFirstName() + " " + u.getLastName()).trim(),
                    u.getRole().getId().toString(), u.getRole().getName(), RoleService.effectivePermissions(u),
                    shopId != null ? shopId.toString() : null));
        });
    }

    /** The till making the current request (only reachable behind ROLE_POS_DEVICE). */
    public PosDevice currentDevice() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DevicePrincipal principal)) {
            throw new ForbiddenException("This request must come from an enrolled till.");
        }
        return deviceRepository.findById(UUID.fromString(principal.deviceId()))
                .orElseThrow(() -> new ForbiddenException("Unknown till."));
    }

    private PosDeviceResponse toResponse(PosDevice d) {
        boolean online = d.getRevokedAt() == null && d.getLastSeenAt() != null
                && d.getLastSeenAt().isAfter(Instant.now().minus(ONLINE_WINDOW));
        return new PosDeviceResponse(d.getId(), d.getName(), d.getPlatform(), d.getAppVersion(), d.getEnrolledAt(),
                d.getEnrolledByName(), d.getLastSeenAt(), online, d.getPendingSales(), d.getRevokedAt());
    }

    private static String normalizeCode(String code) {
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
