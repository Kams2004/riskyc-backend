package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.EnterpriseApplicationRequest;
import com.fashion.Riskyc.dto.request.EnterpriseApprovalRequest;
import com.fashion.Riskyc.dto.response.CategorySummaryResponse;
import com.fashion.Riskyc.dto.response.EnterpriseResponse;
import com.fashion.Riskyc.dto.response.EnterpriseStatusResponse;
import com.fashion.Riskyc.entity.AccountStatus;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Category;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.EnterpriseStatus;
import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.security.CurrentAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Seller onboarding: anonymous application -> super-admin review (with a
 * non-persisted, always-recomputed category suggestion) -> approval, which
 * provisions the enterprise's first ("Owner") AdminUser and emails them
 * temporary credentials.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EnterpriseService {

    private static final String TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    /** The seller's own first role, granted everything it needs to run its own slice of the panel — never a platform-only permission like MANAGE_ENTERPRISES. */
    private static final Set<Permission> DEFAULT_OWNER_PERMISSIONS = Set.of(
            Permission.VIEW_DASHBOARD,
            Permission.VIEW_ORDERS, Permission.MANAGE_ORDERS,
            Permission.VIEW_TREATMENT, Permission.MANAGE_TREATMENT,
            Permission.VIEW_PRODUCTS, Permission.MANAGE_PRODUCTS,
            Permission.VIEW_CATEGORIES, Permission.MANAGE_CATEGORIES,
            Permission.VIEW_USERS, Permission.MANAGE_USERS
    );

    private final EnterpriseRepository enterpriseRepository;
    private final CategoryRepository categoryRepository;
    private final AdminUserRepository adminUserRepository;
    private final RoleService roleService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public EnterpriseResponse submitApplication(EnterpriseApplicationRequest request) {
        Enterprise enterprise = enterpriseRepository.saveAndFlush(Enterprise.builder()
                .name(request.businessName())
                .description(request.description())
                .businessType(request.businessType())
                .contactFirstName(request.contactFirstName())
                .contactLastName(request.contactLastName())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .whatDoYouSell(request.whatDoYouSell())
                .estimatedCatalogSize(request.estimatedCatalogSize())
                .status(EnterpriseStatus.PENDING)
                .build());
        return toResponse(enterprise);
    }

    @Transactional(readOnly = true)
    public List<EnterpriseResponse> listPending() {
        return enterpriseRepository.findAllByStatusOrderByCreatedAtDesc(EnterpriseStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EnterpriseResponse> listAll() {
        return enterpriseRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EnterpriseResponse getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /**
     * Public — no VIEW_ENTERPRISES required. Requires the applicant's own
     * contact email to match, so this can't be used to enumerate other
     * applicants' status by guessing ids. Used by the "Become a Seller"
     * wizard's returning-applicant status check.
     */
    @Transactional(readOnly = true)
    public EnterpriseStatusResponse getPublicStatus(UUID id, String contactEmail) {
        Enterprise enterprise = getOrThrow(id);
        if (contactEmail == null || !enterprise.getContactEmail().equalsIgnoreCase(contactEmail.trim())) {
            throw ResourceNotFoundException.of("Enterprise", id);
        }
        return new EnterpriseStatusResponse(enterprise.getName(), enterprise.getStatus());
    }

    public EnterpriseResponse approve(UUID id, EnterpriseApprovalRequest request) {
        Enterprise enterprise = getOrThrow(id);
        if (enterprise.getStatus() != EnterpriseStatus.PENDING) {
            throw new BadRequestException("This application has already been reviewed.");
        }
        List<Category> categories = request.categoryIds().stream()
                .map(categoryId -> categoryRepository.findById(categoryId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Category", categoryId)))
                .toList();

        enterprise.setStatus(EnterpriseStatus.ACTIVE);
        enterprise.getCategories().clear();
        enterprise.getCategories().addAll(categories);
        enterprise.setApprovedByName(CurrentAdmin.nameOrNull());
        enterprise.setApprovedAt(Instant.now());

        Role ownerRole = roleService.createScopedRole(enterprise, "Owner", DEFAULT_OWNER_PERMISSIONS);

        String tempPassword = generateTempPassword();
        AdminUser owner = adminUserRepository.saveAndFlush(AdminUser.builder()
                .firstName(firstNonBlank(enterprise.getContactFirstName(), enterprise.getName()))
                .lastName(firstNonBlank(enterprise.getContactLastName(), ""))
                .email(enterprise.getContactEmail())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(ownerRole)
                .enterprise(enterprise)
                .status(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build());

        emailService.sendApplicationDecision(enterprise, true, null);
        emailService.sendSellerInvite(enterprise, owner, tempPassword);

        return toResponse(enterprise);
    }

    public EnterpriseResponse reject(UUID id, String reason) {
        Enterprise enterprise = getOrThrow(id);
        if (enterprise.getStatus() != EnterpriseStatus.PENDING) {
            throw new BadRequestException("This application has already been reviewed.");
        }
        enterprise.setStatus(EnterpriseStatus.REJECTED);
        emailService.sendApplicationDecision(enterprise, false, reason);
        return toResponse(enterprise);
    }

    /**
     * Not ML — a cheap keyword/substring overlap between the applicant's own
     * words (businessType + whatDoYouSell) and every existing Category's
     * name/nameFr, ranked by overlap count. A starting point the super admin
     * confirms or overrides at approval time; never persisted, so it's always
     * recomputed against the current category list on every read.
     */
    private List<CategorySummaryResponse> suggestCategories(Enterprise enterprise) {
        Set<String> tokens = tokenize(enterprise.getBusinessType()).collect(Collectors.toSet());
        tokens.addAll(tokenize(enterprise.getWhatDoYouSell()).toList());
        if (tokens.isEmpty()) return List.of();

        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new ScoredCategory(category, overlapScore(tokens, category)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredCategory::score).reversed())
                .limit(5)
                .map(scored -> toSummary(scored.category()))
                .toList();
    }

    private int overlapScore(Set<String> applicantTokens, Category category) {
        Set<String> categoryTokens = tokenize(category.getName()).collect(Collectors.toSet());
        if (category.getNameFr() != null) {
            categoryTokens.addAll(tokenize(category.getNameFr()).toList());
        }
        int score = 0;
        for (String applicantToken : applicantTokens) {
            for (String categoryToken : categoryTokens) {
                if (applicantToken.contains(categoryToken) || categoryToken.contains(applicantToken)) {
                    score++;
                }
            }
        }
        return score;
    }

    private Stream<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Stream.empty();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+"))
                .filter(token -> token.length() >= 3);
    }

    private record ScoredCategory(Category category, int score) {
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(random.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String firstNonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private Enterprise getOrThrow(UUID id) {
        return enterpriseRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Enterprise", id));
    }

    private CategorySummaryResponse toSummary(Category category) {
        return new CategorySummaryResponse(category.getId(), category.getSlug(), category.getName());
    }

    private EnterpriseResponse toResponse(Enterprise enterprise) {
        List<CategorySummaryResponse> categories = enterprise.getCategories().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(CategorySummaryResponse::name))
                .toList();
        List<CategorySummaryResponse> suggested = enterprise.getStatus() == EnterpriseStatus.PENDING
                ? suggestCategories(enterprise)
                : List.of();
        return new EnterpriseResponse(
                enterprise.getId(), enterprise.getName(), enterprise.getDescription(), enterprise.getBusinessType(),
                enterprise.getStatus(), enterprise.getContactFirstName(), enterprise.getContactLastName(),
                enterprise.getContactEmail(), enterprise.getContactPhone(), enterprise.getWhatDoYouSell(),
                enterprise.getEstimatedCatalogSize(), categories, suggested,
                enterprise.getApprovedByName(), enterprise.getApprovedAt(), enterprise.getCreatedAt()
        );
    }
}
