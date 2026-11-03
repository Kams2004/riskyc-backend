package com.fashion.Riskyc.service;

import com.fashion.Riskyc.dto.request.EnterpriseApplicationRequest;
import com.fashion.Riskyc.dto.request.EnterpriseApprovalRequest;
import com.fashion.Riskyc.dto.response.EnterpriseResponse;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Category;
import com.fashion.Riskyc.entity.Enterprise;
import com.fashion.Riskyc.entity.EnterpriseStatus;
import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.entity.Role;
import com.fashion.Riskyc.exception.BadRequestException;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the status-transition guards and provisioning flow that were
 * previously only proven by hand via psql+curl against the dev stack.
 */
@ExtendWith(MockitoExtension.class)
class EnterpriseServiceTest {

    @Mock
    private EnterpriseRepository enterpriseRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private RoleService roleService;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EnterpriseService enterpriseService;

    private Enterprise pendingEnterprise;

    @BeforeEach
    void setUp() {
        pendingEnterprise = Enterprise.builder()
                .id(UUID.randomUUID())
                .name("Test Seller")
                .businessType("Fashion retailer")
                .whatDoYouSell("Dresses and jerseys")
                .contactEmail("seller@example.com")
                .contactFirstName("Test")
                .contactLastName("Seller")
                .status(EnterpriseStatus.PENDING)
                .build();
    }

    // ── approve() ──────────────────────────────────────────────────────

    @Test
    void approve_rejectsAnAlreadyActiveApplication() {
        pendingEnterprise.setStatus(EnterpriseStatus.ACTIVE);
        when(enterpriseRepository.findById(pendingEnterprise.getId())).thenReturn(java.util.Optional.of(pendingEnterprise));

        assertThatThrownBy(() -> enterpriseService.approve(pendingEnterprise.getId(), new EnterpriseApprovalRequest(List.of())))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(roleService, emailService);
    }

    @Test
    void approve_rejectsAnAlreadyRejectedApplication() {
        pendingEnterprise.setStatus(EnterpriseStatus.REJECTED);
        when(enterpriseRepository.findById(pendingEnterprise.getId())).thenReturn(java.util.Optional.of(pendingEnterprise));

        assertThatThrownBy(() -> enterpriseService.approve(pendingEnterprise.getId(), new EnterpriseApprovalRequest(List.of())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void approve_activatesTheEnterpriseAndProvisionsAnOwnerAdmin() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder().id(categoryId).slug("dresses").name("Dresses").build();
        Role ownerRole = Role.builder().id(UUID.randomUUID()).name("Owner").enterprise(pendingEnterprise).build();

        when(enterpriseRepository.findById(pendingEnterprise.getId())).thenReturn(java.util.Optional.of(pendingEnterprise));
        when(categoryRepository.findById(categoryId)).thenReturn(java.util.Optional.of(category));
        when(roleService.createScopedRole(any(), any(), any())).thenReturn(ownerRole);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(adminUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        EnterpriseResponse response = enterpriseService.approve(pendingEnterprise.getId(), new EnterpriseApprovalRequest(List.of(categoryId)));

        assertThat(response.status()).isEqualTo(EnterpriseStatus.ACTIVE);
        assertThat(response.categories()).hasSize(1);
        assertThat(pendingEnterprise.getStatus()).isEqualTo(EnterpriseStatus.ACTIVE);

        verify(roleService).createScopedRole(eq(pendingEnterprise), eq("Owner"), any());
        verify(emailService).sendApplicationDecision(pendingEnterprise, true, null);
        verify(emailService).sendSellerInvite(eq(pendingEnterprise), any(AdminUser.class), any());
    }

    @Test
    void approve_neverGrantsAPlatformOnlyPermissionToTheOwnerRole() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder().id(categoryId).slug("dresses").name("Dresses").build();
        Role ownerRole = Role.builder().id(UUID.randomUUID()).name("Owner").build();

        when(enterpriseRepository.findById(pendingEnterprise.getId())).thenReturn(java.util.Optional.of(pendingEnterprise));
        when(categoryRepository.findById(categoryId)).thenReturn(java.util.Optional.of(category));
        when(roleService.createScopedRole(any(), any(), any())).thenReturn(ownerRole);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(adminUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        enterpriseService.approve(pendingEnterprise.getId(), new EnterpriseApprovalRequest(List.of(categoryId)));

        org.mockito.ArgumentCaptor<Set<Permission>> permissionsCaptor = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(roleService).createScopedRole(any(), any(), permissionsCaptor.capture());
        assertThat(permissionsCaptor.getValue())
                .doesNotContain(Permission.MANAGE_ENTERPRISES, Permission.VIEW_ENTERPRISES);
    }

    // ── reject() ───────────────────────────────────────────────────────

    @Test
    void reject_rejectsAnAlreadyReviewedApplication() {
        pendingEnterprise.setStatus(EnterpriseStatus.ACTIVE);
        when(enterpriseRepository.findById(pendingEnterprise.getId())).thenReturn(java.util.Optional.of(pendingEnterprise));

        assertThatThrownBy(() -> enterpriseService.reject(pendingEnterprise.getId(), "not a fit"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reject_marksTheApplicationRejectedAndEmailsTheDecision() {
        when(enterpriseRepository.findById(pendingEnterprise.getId())).thenReturn(java.util.Optional.of(pendingEnterprise));

        EnterpriseResponse response = enterpriseService.reject(pendingEnterprise.getId(), "not a fit");

        assertThat(response.status()).isEqualTo(EnterpriseStatus.REJECTED);
        verify(emailService).sendApplicationDecision(pendingEnterprise, false, "not a fit");
    }

    // ── suspend() / reactivate() ──────────────────────────────────────

    @Test
    void suspend_refusesThePlatformDefaultEnterprise() {
        Enterprise platformDefault = Enterprise.builder()
                .id(UUID.randomUUID()).name("Riskyc Fashion").status(EnterpriseStatus.ACTIVE).platformDefault(true).build();
        when(enterpriseRepository.findById(platformDefault.getId())).thenReturn(java.util.Optional.of(platformDefault));

        assertThatThrownBy(() -> enterpriseService.suspend(platformDefault.getId()))
                .isInstanceOf(BadRequestException.class);
        assertThat(platformDefault.getStatus()).isEqualTo(EnterpriseStatus.ACTIVE);
    }

    @Test
    void suspend_refusesAnEnterpriseThatIsNotActive() {
        Enterprise suspended = Enterprise.builder().id(UUID.randomUUID()).name("X").status(EnterpriseStatus.SUSPENDED).build();
        when(enterpriseRepository.findById(suspended.getId())).thenReturn(java.util.Optional.of(suspended));

        assertThatThrownBy(() -> enterpriseService.suspend(suspended.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void suspend_flipsAnActiveEnterpriseToSuspended() {
        Enterprise active = Enterprise.builder().id(UUID.randomUUID()).name("X").status(EnterpriseStatus.ACTIVE).build();
        when(enterpriseRepository.findById(active.getId())).thenReturn(java.util.Optional.of(active));

        EnterpriseResponse response = enterpriseService.suspend(active.getId());

        assertThat(response.status()).isEqualTo(EnterpriseStatus.SUSPENDED);
    }

    @Test
    void reactivate_refusesAnEnterpriseThatIsNotSuspended() {
        Enterprise active = Enterprise.builder().id(UUID.randomUUID()).name("X").status(EnterpriseStatus.ACTIVE).build();
        when(enterpriseRepository.findById(active.getId())).thenReturn(java.util.Optional.of(active));

        assertThatThrownBy(() -> enterpriseService.reactivate(active.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reactivate_restoresASuspendedEnterpriseToActive() {
        Enterprise suspended = Enterprise.builder().id(UUID.randomUUID()).name("X").status(EnterpriseStatus.SUSPENDED).build();
        when(enterpriseRepository.findById(suspended.getId())).thenReturn(java.util.Optional.of(suspended));

        EnterpriseResponse response = enterpriseService.reactivate(suspended.getId());

        assertThat(response.status()).isEqualTo(EnterpriseStatus.ACTIVE);
    }

    // ── suggestCategories() (exercised indirectly via submitApplication, since it's a private helper) ──

    @Test
    void submitApplication_suggestsCategoriesThatOverlapWithTheApplicantsOwnWords() {
        Category dresses = Category.builder().id(UUID.randomUUID()).slug("dresses").name("Dresses").build();
        Category jerseys = Category.builder().id(UUID.randomUUID()).slug("jerseys").name("Jerseys").build();
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(dresses, jerseys));
        when(enterpriseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        EnterpriseApplicationRequest request = new EnterpriseApplicationRequest(
                "Test Seller", null, "Fashion retailer", "Test", "Seller",
                "seller@example.com", null, "Dresses and jerseys for women", null);

        EnterpriseResponse response = enterpriseService.submitApplication(request);

        assertThat(response.suggestedCategories()).extracting("name").containsExactlyInAnyOrder("Dresses", "Jerseys");
    }

    @Test
    void submitApplication_suggestsNothingWhenNoCategoryOverlaps() {
        Category electronics = Category.builder().id(UUID.randomUUID()).slug("electronics").name("Electronics").build();
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(electronics));
        when(enterpriseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        EnterpriseApplicationRequest request = new EnterpriseApplicationRequest(
                "Test Seller", null, "Fashion retailer", "Test", "Seller",
                "seller@example.com", null, "Shoes and handbags", null);

        EnterpriseResponse response = enterpriseService.submitApplication(request);

        assertThat(response.suggestedCategories()).isEmpty();
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
