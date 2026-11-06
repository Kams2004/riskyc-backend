package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.PaymentSettingsRequest;
import com.fashion.Riskyc.dto.response.PaymentSettingsResponse;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

import static com.fashion.Riskyc.service.PaymentAccountService.AMOUNT_PLACEHOLDER;
import static com.fashion.Riskyc.service.PaymentAccountService.normalizeCode;

/** Reads and saves the payment numbers of the signed-in admin's own shop; platform staff manage Riskyc's own store. */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentSettingsService {

    /** What a USSD dial code can contain: digits, * and #, plus the amount placeholder. */
    private static final Pattern DIAL_CODE = Pattern.compile("[0-9*#]*(\\{amount\\}[0-9*#]*)+");

    private final EnterpriseRepository enterpriseRepository;
    private final PaymentAccountService paymentAccountService;

    @Transactional(readOnly = true)
    public PaymentSettingsResponse get() {
        return toResponse(currentShop());
    }

    public PaymentSettingsResponse update(PaymentSettingsRequest request) {
        Enterprise shop = currentShop();
        String omCode = checkedCode(request.orangeMoneyCode(), "Orange Money");
        String omName = blankToNull(request.orangeMoneyName());
        String mmCode = checkedCode(request.mobileMoneyCode(), "MTN Mobile Money");
        String mmName = blankToNull(request.mobileMoneyName());
        requirePair(omCode, omName, "Orange Money");
        requirePair(mmCode, mmName, "MTN Mobile Money");
        shop.setOrangeMoneyCode(omCode);
        shop.setOrangeMoneyName(omName);
        shop.setMobileMoneyCode(mmCode);
        shop.setMobileMoneyName(mmName);
        return toResponse(enterpriseRepository.save(shop));
    }

    private Enterprise currentShop() {
        UUID scopeId = CurrentAdmin.enterpriseIdOrNull();
        if (scopeId != null) {
            return enterpriseRepository.findById(scopeId).orElseThrow(() -> ResourceNotFoundException.of("Enterprise", scopeId));
        }
        return enterpriseRepository.findByPlatformDefaultTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Riskyc's own store isn't set up yet."));
    }

    private static String checkedCode(String raw, String label) {
        String code = normalizeCode(raw);
        if (code == null) return null;
        String compact = code.replace(" ", "");
        if (!compact.contains(AMOUNT_PLACEHOLDER) || !DIAL_CODE.matcher(compact).matches()) {
            throw new BadRequestException(label + ": the code must be the number customers dial, using only digits, * and #, with "
                    + AMOUNT_PLACEHOLDER + " where the amount goes — for example *126*14*6XXXXXXXX*" + AMOUNT_PLACEHOLDER + "#");
        }
        return compact;
    }

    /** A method is either fully set up (code + name the customer will see) or not offered at all. */
    private static void requirePair(String code, String name, String label) {
        if ((code == null) != (name == null)) {
            throw new BadRequestException(label + ": enter both the code and the account name, or leave both empty to stop offering it.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private PaymentSettingsResponse toResponse(Enterprise e) {
        return new PaymentSettingsResponse(e.getName(), e.getOrangeMoneyCode(), e.getOrangeMoneyName(),
                e.getMobileMoneyCode(), e.getMobileMoneyName(), paymentAccountService.acceptsPayments(e));
    }
}
