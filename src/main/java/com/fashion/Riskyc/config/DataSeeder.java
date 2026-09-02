package com.fashion.Riskyc.config;

import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds a minimal, working dataset on first boot (empty DB only) so the API
 * is immediately usable without a separate admin-setup step: the storefront
 * categories, a Super Admin role with every permission, and one admin login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final RoleRepository roleRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedCategories();
        Role superAdminRole = seedRoles();
        seedAdminUser(superAdminRole);
        backfillManagePermissionsImplyView();
    }

    /**
     * A role saved before RoleService started normalizing permissions (or
     * edited directly in the DB) can hold a MANAGE_X permission with no
     * matching VIEW_X — e.g. a role with only "Manage Orders" can't see the
     * order list at all, so there's nothing to manage. Runs on every boot;
     * a no-op once every role is already normalized.
     */
    private void backfillManagePermissionsImplyView() {
        for (Role role : roleRepository.findAll()) {
            Set<Permission> permissions = new HashSet<>(role.getPermissions());
            boolean changed = false;
            for (Permission p : role.getPermissions()) {
                if (p.name().startsWith("MANAGE_")) {
                    try {
                        Permission view = Permission.valueOf("VIEW_" + p.name().substring("MANAGE_".length()));
                        changed |= permissions.add(view);
                    } catch (IllegalArgumentException ignored) {
                        // No matching VIEW_ counterpart for this permission — nothing to imply.
                    }
                }
            }
            if (changed) {
                role.setPermissions(permissions);
                roleRepository.save(role);
                log.info("Backfilled implied View permissions for role '{}'", role.getName());
            }
        }
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) return;

        categoryRepository.save(Category.builder()
                .slug("dresses").name("Dresses").icon("👗")
                .build());

        Category fashion = categoryRepository.save(Category.builder()
                .slug("fashion").name("Fashion").icon("✨")
                .build());
        fashion.getSubcategories().addAll(List.of(
                Subcategory.builder().slug("tops").name("Tops & Blouses").category(fashion).build(),
                Subcategory.builder().slug("bottoms").name("Pants & Trousers").category(fashion).build()
        ));
        categoryRepository.save(fashion);

        Category dresses = categoryRepository.findBySlug("dresses").orElseThrow();
        dresses.getSubcategories().addAll(List.of(
                Subcategory.builder().slug("evening").name("Evening Dresses").category(dresses).build(),
                Subcategory.builder().slug("casual").name("Casual Dresses").category(dresses).build()
        ));
        categoryRepository.save(dresses);

        categoryRepository.save(Category.builder()
                .slug("jerseys").name("Jerseys").icon("👕")
                .build());

        log.info("Seeded default categories");
    }

    private Role seedRoles() {
        if (roleRepository.count() > 0) {
            return roleRepository.findAll().get(0);
        }
        Role superAdmin = roleRepository.save(Role.builder()
                .name("Super Admin")
                .description("Full access to all features")
                .permissions(Set.of(Permission.values()))
                .build());

        roleRepository.save(Role.builder()
                .name("Store Manager")
                .description("Manages products, orders and categories")
                .permissions(Set.of(
                        Permission.VIEW_DASHBOARD, Permission.VIEW_ORDERS, Permission.MANAGE_ORDERS,
                        Permission.VIEW_TREATMENT, Permission.MANAGE_TREATMENT,
                        Permission.VIEW_PRODUCTS, Permission.MANAGE_PRODUCTS,
                        Permission.VIEW_CATEGORIES, Permission.MANAGE_CATEGORIES, Permission.VIEW_CHAT
                ))
                .build());

        roleRepository.save(Role.builder()
                .name("Support Agent")
                .description("Handles customer chat and views orders")
                .permissions(Set.of(Permission.VIEW_DASHBOARD, Permission.VIEW_ORDERS, Permission.VIEW_CHAT, Permission.MANAGE_CHAT))
                .build());

        log.info("Seeded default roles");
        return superAdmin;
    }

    private void seedAdminUser(Role superAdminRole) {
        if (adminUserRepository.count() > 0) return;
        adminUserRepository.save(AdminUser.builder()
                .firstName("Admin")
                .lastName("Riskyc")
                .email("admin@riskyc.com")
                .passwordHash(passwordEncoder.encode("riskyc2025"))
                .role(superAdminRole)
                .status(AccountStatus.ACTIVE)
                .build());
        log.info("Seeded default admin user (admin@riskyc.com)");
    }
}
