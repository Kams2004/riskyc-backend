package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.GoogleAuthRequest;
import com.fashion.Riskyc.dto.request.LoginRequest;
import com.fashion.Riskyc.dto.request.RegisterCustomerRequest;
import com.fashion.Riskyc.dto.response.CustomerResponse;
import com.fashion.Riskyc.dto.response.ReferralSummaryResponse;
import com.fashion.Riskyc.entity.Customer;
import com.fashion.Riskyc.entity.CustomerStatus;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    // Excludes visually ambiguous characters (0/O, 1/I) so a code read off a
    // screen or spoken aloud can be typed back in without guesswork.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    private final RestClient restClient = RestClient.create();

    /**
     * Google OAuth Client ID this backend accepts tokens for — when blank
     * (not configured yet), the audience check is skipped so the flow still
     * works end-to-end in dev before real credentials exist; set
     * GOOGLE_CLIENT_ID once you have one from Google Cloud Console.
     */
    @Value("${app.google.client-id:}")
    private String googleClientId;

    public CustomerResponse register(RegisterCustomerRequest request) {
        if (customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        Customer customer = customerRepository.saveAndFlush(Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(CustomerStatus.ACTIVE)
                .lastLogin(Instant.now())
                .referralCode(generateUniqueReferralCode())
                .acronym(generateDefaultAcronym())
                .referredBy(resolveReferrer(request.referralCode()))
                .build());
        return toResponse(customer);
    }

    public CustomerResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));
        if (customer.getPasswordHash() == null || !passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }
        if (customer.getStatus() == CustomerStatus.BLOCKED) {
            throw new BadRequestException("This account has been blocked. Please contact support.");
        }
        customer.setLastLogin(Instant.now());
        return toResponse(customer);
    }

    /**
     * Verifies the ID token with Google, then finds-or-creates the matching
     * customer — same "no separate session token" pattern as email login,
     * the frontend just caches the returned CustomerResponse.
     */
    public CustomerResponse loginWithGoogle(GoogleAuthRequest request) {
        Map<String, Object> claims = verifyGoogleIdToken(request.idToken());

        String googleId = String.valueOf(claims.get("sub"));
        String email = String.valueOf(claims.get("email"));
        if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
            throw new BadRequestException("Google sign-in did not return the expected account details");
        }

        Customer customer = customerRepository.findByGoogleId(googleId)
                .or(() -> customerRepository.findByEmailIgnoreCase(email))
                .orElse(null);

        if (customer == null) {
            String firstName = firstNonBlank(String.valueOf(claims.getOrDefault("given_name", "")), "Google");
            String lastName = firstNonBlank(String.valueOf(claims.getOrDefault("family_name", "")), "User");
            customer = Customer.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .status(CustomerStatus.ACTIVE)
                    .lastLogin(Instant.now())
                    .referralCode(generateUniqueReferralCode())
                    .acronym(generateDefaultAcronym())
                    .referredBy(resolveReferrer(request.referralCode()))
                    .googleId(googleId)
                    .build();
        } else {
            if (customer.getStatus() == CustomerStatus.BLOCKED) {
                throw new BadRequestException("This account has been blocked. Please contact support.");
            }
            if (customer.getGoogleId() == null) {
                customer.setGoogleId(googleId);
            }
            customer.setLastLogin(Instant.now());
        }
        Customer saved = customerRepository.saveAndFlush(customer);
        return toResponse(saved);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyGoogleIdToken(String idToken) {
        Map<String, Object> claims;
        try {
            claims = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Invalid or expired Google sign-in token");
        }
        if (claims == null) {
            throw new BadRequestException("Invalid or expired Google sign-in token");
        }
        if (googleClientId != null && !googleClientId.isBlank()) {
            Object aud = claims.get("aud");
            if (aud == null || !googleClientId.equals(aud.toString())) {
                throw new BadRequestException("This Google sign-in token was not issued for this app");
            }
        }
        return claims;
    }

    private String firstNonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private Customer resolveReferrer(String referralCode) {
        if (referralCode == null || referralCode.isBlank()) return null;
        return customerRepository.findByReferralCodeIgnoreCase(referralCode.trim()).orElse(null);
    }

    private String generateUniqueReferralCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (customerRepository.existsByReferralCode(code));
        return code;
    }

    private String generateDefaultAcronym() {
        return "User" + (1000 + random.nextInt(9000));
    }

    /** Backfills referral fields for rows created before this feature existed. */
    private void ensureReferralFields(Customer c) {
        boolean dirty = false;
        if (c.getReferralCode() == null || c.getReferralCode().isBlank()) {
            c.setReferralCode(generateUniqueReferralCode());
            dirty = true;
        }
        if (c.getAcronym() == null || c.getAcronym().isBlank()) {
            c.setAcronym(generateDefaultAcronym());
            dirty = true;
        }
        if (dirty) {
            customerRepository.save(c);
        }
    }

    public CustomerResponse updateAcronym(UUID id, String acronym) {
        if (acronym == null || acronym.trim().isEmpty()) {
            throw new BadRequestException("Display name cannot be empty");
        }
        String trimmed = acronym.trim();
        if (trimmed.length() > 24) {
            throw new BadRequestException("Display name must be 24 characters or fewer");
        }
        Customer customer = getOrThrow(id);
        customer.setAcronym(trimmed);
        return toResponse(customer);
    }

    // Not read-only: ensureReferralFields() below may backfill+save legacy rows.
    public ReferralSummaryResponse getReferralSummary(UUID id) {
        Customer customer = getOrThrow(id);
        ensureReferralFields(customer);

        List<Customer> direct = customerRepository.findByReferredByIdOrderByCreatedAtDesc(id);
        List<ReferralSummaryResponse.ReferralEntry> entries = direct.stream()
                .map(r -> new ReferralSummaryResponse.ReferralEntry(
                        r.getAcronym() != null ? r.getAcronym() : "Referral",
                        r.getCreatedAt(),
                        customerRepository.countByReferredById(r.getId())
                ))
                .toList();
        long indirectCount = entries.stream().mapToLong(ReferralSummaryResponse.ReferralEntry::referredCount).sum();

        return new ReferralSummaryResponse(
                customer.getReferralCode(),
                customer.getAcronym(),
                customer.getReferredBy() != null ? customer.getReferredBy().getAcronym() : null,
                direct.size(),
                indirectCount,
                entries
        );
    }

    // Not read-only: toResponse() -> ensureReferralFields() may backfill+save legacy rows.
    public List<CustomerResponse> listAll() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CustomerResponse updateStatus(UUID id, CustomerStatus status) {
        Customer customer = getOrThrow(id);
        customer.setStatus(status);
        return toResponse(customer);
    }

    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Customer", id);
        }
        customerRepository.deleteById(id);
    }

    private Customer getOrThrow(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
    }

    private CustomerResponse toResponse(Customer c) {
        ensureReferralFields(c);
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhone(), c.getStatus(), c.getCreatedAt(), c.getLastLogin(),
                c.getReferralCode(), c.getAcronym(),
                c.getReferredBy() != null ? c.getReferredBy().getAcronym() : null);
    }
}
