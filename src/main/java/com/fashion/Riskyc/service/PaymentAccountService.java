package com.fashion.Riskyc.service;

import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.PaymentMethod;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a shop's customers pay. Every seller receives its own payments into its own Orange Money / MTN accounts and
 * validates them itself; Riskyc's own store (the platform-default enterprise) uses the numbers from
 * {@code app.payment.*}, copied onto it at startup so they show and can be edited like any other shop's.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAccountService {

    /** Where the amount goes in a stored dial code. */
    public static final String AMOUNT_PLACEHOLDER = "{amount}";

    private final EnterpriseRepository enterpriseRepository;

    @Value("${app.payment.orange-money-code}")
    private String platformOrangeMoneyCode;

    @Value("${app.payment.orange-money-name}")
    private String platformOrangeMoneyName;

    @Value("${app.payment.mobile-money-code}")
    private String platformMobileMoneyCode;

    @Value("${app.payment.mobile-money-name}")
    private String platformMobileMoneyName;

    /** One way to pay a shop: the dial code (with the amount placeholder) and the name the customer sees on the transfer. */
    public record Account(PaymentMethod method, String codeTemplate, String accountName) {
        public String codeFor(BigDecimal amount) {
            String whole = amount.setScale(0, RoundingMode.DOWN).toPlainString();
            return codeTemplate.replace(AMOUNT_PLACEHOLDER, whole);
        }
    }

    /** The methods this shop's customers can pay with — empty means the shop can't be bought from online yet. */
    public List<Account> accountsFor(Enterprise enterprise) {
        List<Account> accounts = new ArrayList<>(2);
        boolean platform = enterprise == null || enterprise.isPlatformDefault();
        String omCode = enterprise != null ? enterprise.getOrangeMoneyCode() : null;
        String omName = enterprise != null ? enterprise.getOrangeMoneyName() : null;
        String mmCode = enterprise != null ? enterprise.getMobileMoneyCode() : null;
        String mmName = enterprise != null ? enterprise.getMobileMoneyName() : null;
        // Riskyc's own store (and pre-marketplace orders with no shop) fall back to the configured platform numbers.
        if (platform && isBlank(omCode) && isBlank(mmCode)) {
            omCode = normalizeCode(platformOrangeMoneyCode);
            omName = platformOrangeMoneyName;
            mmCode = normalizeCode(platformMobileMoneyCode);
            mmName = platformMobileMoneyName;
        }
        if (!isBlank(omCode) && !isBlank(omName)) accounts.add(new Account(PaymentMethod.ORANGE_MONEY, omCode, omName.trim()));
        if (!isBlank(mmCode) && !isBlank(mmName)) accounts.add(new Account(PaymentMethod.MOBILE_MONEY, mmCode, mmName.trim()));
        return accounts;
    }

    public Optional<Account> accountFor(Enterprise enterprise, PaymentMethod method) {
        return accountsFor(enterprise).stream().filter(a -> a.method() == method).findFirst();
    }

    public boolean acceptsPayments(Enterprise enterprise) {
        return !accountsFor(enterprise).isEmpty();
    }

    /** Trims, accepts the older "%s" placeholder, and turns blank into null (= method not offered). */
    public static String normalizeCode(String code) {
        if (isBlank(code)) return null;
        return code.trim().replace("%s", AMOUNT_PLACEHOLDER);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Copies the configured platform numbers onto Riskyc's own store the first time, so its admins see and manage them. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedPlatformAccounts() {
        enterpriseRepository.findAll().stream()
                .filter(Enterprise::isPlatformDefault)
                .filter(e -> isBlank(e.getOrangeMoneyCode()) && isBlank(e.getMobileMoneyCode()))
                .forEach(e -> {
                    e.setOrangeMoneyCode(normalizeCode(platformOrangeMoneyCode));
                    e.setOrangeMoneyName(platformOrangeMoneyName);
                    e.setMobileMoneyCode(normalizeCode(platformMobileMoneyCode));
                    e.setMobileMoneyName(platformMobileMoneyName);
                    enterpriseRepository.save(e);
                    log.info("Riskyc's own store now holds the payment numbers from app.payment.*");
                });
    }
}
