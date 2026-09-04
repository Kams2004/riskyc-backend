package com.fashion.Riskyc.config;

import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.RoleRepository;
import com.fashion.Riskyc.repository.SubcategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
    private final SubcategoryRepository subcategoryRepository;
    private final RoleRepository roleRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    private static final Pattern VALID_SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    @Override
    public void run(String... args) {
        dropStaleEnumCheckConstraints();
        seedCategories();
        Role superAdminRole = seedRoles();
        seedAdminUser(superAdminRole);
        backfillManagePermissionsImplyView();
        backfillMalformedCategorySlugs();
    }

    /**
     * Postgres CHECK constraints Hibernate generates for @Enumerated(STRING)
     * columns freeze in whichever enum values existed the moment the table
     * was first created — ddl-auto=update adds missing tables/columns on
     * later boots but never revisits an existing constraint, so every
     * permission added after role_permissions first existed would violate
     * it and crash the whole app on startup (exactly what SEND_PACKAGING_MESSAGE
     * just did in production). Role.permissions now maps to a plain varchar
     * with no such constraint going forward; this drops the leftover
     * constraint from before that fix, on every boot — a no-op once it's
     * gone, and runs before anything else touches role_permissions.
     */
    private void dropStaleEnumCheckConstraints() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE role_permissions DROP CONSTRAINT IF EXISTS role_permissions_permission_check");
        } catch (SQLException e) {
            log.warn("Could not drop stale role_permissions check constraint (harmless if it was already gone)", e);
        }
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
            // Treatment reuses GET /api/orders under the hood (filtered
            // client-side) — a Treatment-only role can claim/complete
            // packaging but never see anything to work on without this too.
            if (permissions.contains(Permission.VIEW_TREATMENT)) {
                changed |= permissions.add(Permission.VIEW_ORDERS);
            }
            // Whoever can validate (MANAGE_ORDERS) or package (MANAGE_TREATMENT)
            // an order should be able to send the packaging-confirmation message
            // without a separate, easy-to-forget checkbox.
            if (permissions.contains(Permission.MANAGE_ORDERS) || permissions.contains(Permission.MANAGE_TREATMENT)) {
                changed |= permissions.add(Permission.SEND_PACKAGING_MESSAGE);
            }
            if (changed) {
                role.setPermissions(permissions);
                roleRepository.save(role);
                log.info("Backfilled implied View permissions for role '{}'", role.getName());
            }
        }
    }

    /**
     * The admin panel's category editor used to slugify a name by only
     * lowercasing it and collapsing whitespace — a name like "Pantalon/Trouser"
     * became the slug "pantalon/trouser", with the "/" carried straight
     * through. Since category URLs are /category/{slug}, that extra slash
     * turns one path segment into two — Next.js then matches the
     * category+subcategory route instead of the plain category one, so the
     * page 404s as "Subcategory not found". The editor now generates clean
     * slugs going forward; this repairs every category/subcategory saved
     * before that fix, on every boot (a no-op once everything is clean).
     */
    private void backfillMalformedCategorySlugs() {
        // findAllByOrderByNameAsc (not findAll) — it eagerly fetches
        // subcategories via @EntityGraph; DataSeeder runs outside any
        // Hibernate session, so a lazy collection would blow up here.
        for (Category category : categoryRepository.findAllByOrderByNameAsc()) {
            if (!VALID_SLUG.matcher(category.getSlug()).matches()) {
                String fixed = uniqueSlug(slugify(category.getName()), categoryRepository::existsBySlug);
                log.info("Fixing malformed category slug '{}' -> '{}'", category.getSlug(), fixed);
                category.setSlug(fixed);
                categoryRepository.save(category);
            }
            Set<String> siblingSlugs = new HashSet<>();
            for (Subcategory sub : category.getSubcategories()) {
                if (!VALID_SLUG.matcher(sub.getSlug()).matches()) {
                    String base = slugify(sub.getName());
                    String fixed = uniqueSlug(base, siblingSlugs::contains);
                    log.info("Fixing malformed subcategory slug '{}' -> '{}' (category '{}')", sub.getSlug(), fixed, category.getSlug());
                    sub.setSlug(fixed);
                    subcategoryRepository.save(sub);
                    siblingSlugs.add(fixed);
                } else {
                    siblingSlugs.add(sub.getSlug());
                }
            }
        }
    }

    private static String uniqueSlug(String base, java.util.function.Predicate<String> taken) {
        String candidate = base;
        int suffix = 2;
        while (taken.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String name) {
        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = withoutAccents.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "category" : slug;
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
