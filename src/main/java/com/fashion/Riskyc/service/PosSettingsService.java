package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.UpdatePosSettingsRequest;
import com.fashion.Riskyc.dto.response.PosSettingsResponse;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.PosSettings;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.PosSettingsRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PosSettingsService {

    private static final String LOGO_FOLDER = "pos-settings";

    private final PosSettingsRepository posSettingsRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final S3MediaService s3MediaService;

    /**
     * Deliberately NOT @Transactional(readOnly = true) — the first call ever
     * made lazily INSERTs the default row (see getOrCreate()/PosSettings'
     * own javadoc), and Postgres rejects a write inside a transaction
     * Spring/the JDBC driver has marked read-only. Caught by actually
     * hitting this against a real empty table, not by a compile check.
     */
    public PosSettingsResponse get() {
        return toResponse(getOrCreate());
    }

    public PosSettingsResponse update(UpdatePosSettingsRequest request) {
        if (!request.cashEnabled() && !request.orangeMoneyEnabled() && !request.mobileMoneyEnabled()) {
            throw new BadRequestException("At least one payment method must stay enabled, or the till couldn't charge anyone.");
        }
        PosSettings settings = getOrCreate();
        settings.setStoreName(request.storeName());
        settings.setStoreAddress(request.storeAddress());
        settings.setStorePhone(request.storePhone());
        settings.setFooterMessage(request.footerMessage());
        settings.setShowCashierName(request.showCashierName());
        settings.setShowSaleId(request.showSaleId());
        settings.setShowLogo(request.showLogo());
        settings.setPaperWidth(request.paperWidth());
        settings.setCashEnabled(request.cashEnabled());
        settings.setOrangeMoneyEnabled(request.orangeMoneyEnabled());
        settings.setMobileMoneyEnabled(request.mobileMoneyEnabled());
        return toResponse(settings);
    }

    public PosSettingsResponse updateLogo(MultipartFile file) {
        PosSettings settings = getOrCreate();
        if (settings.getLogoKey() != null) {
            s3MediaService.delete(settings.getLogoKey());
        }
        settings.setLogoKey(s3MediaService.upload(file, LOGO_FOLDER));
        return toResponse(settings);
    }

    public PosSettingsResponse deleteLogo() {
        PosSettings settings = getOrCreate();
        if (settings.getLogoKey() != null) {
            s3MediaService.delete(settings.getLogoKey());
            settings.setLogoKey(null);
        }
        return toResponse(settings);
    }

    /** The caller's own till settings — their seller's row, or the platform row for unscoped staff. */
    private PosSettings getOrCreate() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId == null) {
            return posSettingsRepository.findFirstByEnterpriseIsNull()
                    .orElseGet(() -> posSettingsRepository.saveAndFlush(PosSettings.builder().build()));
        }
        return posSettingsRepository.findFirstByEnterpriseId(scopeId).orElseGet(() -> {
            Enterprise enterprise = enterpriseRepository.findById(scopeId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId));
            return posSettingsRepository.saveAndFlush(PosSettings.builder()
                    .enterprise(enterprise)
                    .storeName(enterprise.getName())
                    .build());
        });
    }

    private PosSettingsResponse toResponse(PosSettings s) {
        String logoUrl = s.getLogoKey() != null ? s3MediaService.getPresignedUrl(s.getLogoKey()) : null;
        return new PosSettingsResponse(
                s.getStoreName(),
                s.getStoreAddress(),
                s.getStorePhone(),
                s.getFooterMessage(),
                s.isShowCashierName(),
                s.isShowSaleId(),
                s.isShowLogo(),
                s.getPaperWidth(),
                logoUrl,
                s.isCashEnabled(),
                s.isOrangeMoneyEnabled(),
                s.isMobileMoneyEnabled()
        );
    }
}
