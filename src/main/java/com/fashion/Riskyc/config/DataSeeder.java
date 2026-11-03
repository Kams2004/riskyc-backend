package com.fashion.Riskyc.config;

import com.fashion.Riskyc.entity.*;
import com.fashion.Riskyc.repository.AdminUserRepository;
import com.fashion.Riskyc.repository.CategoryRepository;
import com.fashion.Riskyc.repository.DeliveryContactRepository;
import com.fashion.Riskyc.repository.EnterpriseRepository;
import com.fashion.Riskyc.repository.OrderRepository;
import com.fashion.Riskyc.repository.PosSaleRepository;
import com.fashion.Riskyc.repository.ProductRepository;
import com.fashion.Riskyc.repository.ProductVariantRepository;
import com.fashion.Riskyc.repository.RoleRepository;
import com.fashion.Riskyc.repository.SubcategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final DeliveryContactRepository deliveryContactRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final OrderRepository orderRepository;
    private final PosSaleRepository posSaleRepository;
    private final PlatformTransactionManager transactionManager;

    /** Seeded quantity for a migration-backfilled variant whose old ProductColor.stock was null ("untracked") — large enough nothing looks sold-out at launch; see ProductVariant#isUntracked(). */
    private static final int UNTRACKED_SENTINEL_QUANTITY = 999_999;

    private static final Pattern VALID_SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    @Override
    public void run(String... args) {
        dropStaleEnumCheckConstraints();
        dropStaleRoleNameUniqueConstraint();
        seedCategories();
        Role superAdminRole = seedRoles();
        seedAdminUser(superAdminRole);
        seedDeliveryContacts();
        backfillManagePermissionsImplyView();
        backfillSuperAdminHasAllPermissions();
        backfillMalformedCategorySlugs();
        backfillProductVariants();
        backfillCashierRole();
        seedPlatformDefaultEnterprise();
        backfillProductsOntoDefaultEnterprise();
        backfillOrdersOntoDefaultEnterprise();
        backfillPosSalesOntoDefaultEnterprise();
    }

    /**
     * Adds a minimal "Cashier" role (ring up POS sales, can't void one or
     * touch anything else) if it doesn't already exist — seedRoles() only
     * ever runs against a truly empty roles table, so a brand-new role
     * added there never reaches a database that was already seeded before
     * this feature shipped (every existing install, this test DB included).
     * Runs every boot but is a no-op once the role exists.
     */
    private void backfillCashierRole() {
        if (roleRepository.findAll().stream().anyMatch(r -> "Cashier".equals(r.getName()))) return;
        roleRepository.save(Role.builder()
                .name("Cashier")
                .description("Rings up in-person sales at the point of sale")
                .permissions(Set.of(Permission.VIEW_POS, Permission.MANAGE_POS))
                .build());
        log.info("Seeded default Cashier role");
    }

    /**
     * One-time (per product), idempotent backfill of the new ProductVariant
     * stock ledger from the old per-color-only ProductColor.stock — runs
     * every boot but skips any product that already has variant rows, so it
     * only ever does real work once per product. Quantity is a best-effort
     * guess, not a real number:
     * - A color with stock==null ("untracked" under the old model) seeds
     *   every size with UNTRACKED_SENTINEL_QUANTITY, flagged untracked=true
     *   so an admin can tell a migrated sentinel from a real count.
     * - A color with a real stock number gets it split evenly across that
     *   product's sizes (remainder on the first), since the old model never
     *   tracked size-level stock at all — a manual recount after migration
     *   is the honest recommendation here, not something this code can fix.
     * A colorless product gets one variant per size with colorName="";
     * a sizeless product/color gets one variant with size="" — "" is the
     * same "no option" convention OrderItem.selectedColor/selectedSize
     * already use, not null.
     */
    private void backfillProductVariants() {
        // run() itself isn't transactional (a CommandLineRunner call has no
        // surrounding session), and @Transactional on a private method that
        // this class calls on itself wouldn't actually be applied anyway
        // (Spring's proxy-based AOP can't intercept self-invocation) — so
        // this opens a real transaction programmatically instead, which
        // keeps the Hibernate session open across the loop below. Without
        // it, product.getColors()/getSizes() throw LazyInitializationException
        // the moment findAll()'s own short-lived session has already closed.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Product product : productRepository.findAll()) {
                if (productVariantRepository.existsByProductId(product.getId())) continue;
                List<String> sizes = product.getSizes().isEmpty() ? List.of("") : product.getSizes();
                if (product.getColors().isEmpty()) {
                    for (String size : sizes) {
                        saveBackfillVariant(product, "", size, UNTRACKED_SENTINEL_QUANTITY, true);
                    }
                } else {
                    for (ProductColor color : product.getColors()) {
                        Integer stock = color.getStock();
                        int sizeCount = sizes.size();
                        for (int i = 0; i < sizeCount; i++) {
                            if (stock == null) {
                                saveBackfillVariant(product, color.getName(), sizes.get(i), UNTRACKED_SENTINEL_QUANTITY, true);
                            } else {
                                int base = stock / sizeCount;
                                int remainder = stock % sizeCount;
                                int qty = base + (i == 0 ? remainder : 0);
                                saveBackfillVariant(product, color.getName(), sizes.get(i), qty, false);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * The marketplace pivot's one-time setup: a single Enterprise row stands
     * in for "Riskyc Fashion itself" rather than special-casing a null
     * owner anywhere — every pre-existing product gets backfilled onto it
     * (see backfillProductsOntoDefaultEnterprise() below), and any future
     * platform-owned listing uses it the same way a real seller would use
     * their own Enterprise row. Runs every boot, no-op once it exists.
     */
    private void seedPlatformDefaultEnterprise() {
        if (enterpriseRepository.findByPlatformDefaultTrue().isPresent()) return;
        enterpriseRepository.save(Enterprise.builder()
                .name("Riskyc Fashion")
                .description("The platform's own catalog.")
                .status(EnterpriseStatus.ACTIVE)
                .contactEmail("contact@riskycfashion.com")
                .platformDefault(true)
                .build());
        log.info("Seeded the platform-default Enterprise");
    }

    /**
     * One-time, idempotent backfill of Product.enterprise onto the
     * platform-default Enterprise for every product that predates this
     * column — same TransactionTemplate reasoning as
     * backfillProductVariants() just above (run() has no surrounding
     * session, and @Transactional on a privately self-invoked method isn't
     * applied by Spring's proxy-based AOP either way). Product.enterprise
     * stays nullable in code until every environment has run this at least
     * once; see Product.java's own javadoc on the field.
     */
    private void backfillProductsOntoDefaultEnterprise() {
        Enterprise platformDefault = enterpriseRepository.findByPlatformDefaultTrue().orElse(null);
        if (platformDefault == null) return; // seedPlatformDefaultEnterprise() runs just before this; defensive only.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Product product : productRepository.findAll()) {
                if (product.getEnterprise() != null) continue;
                product.setEnterprise(platformDefault);
                productRepository.save(product);
            }
        });
    }

    /**
     * One-time, idempotent backfill of Order.enterprise onto the
     * platform-default Enterprise for every order that predates this column
     * (Phase 8 of the marketplace pivot) — same reasoning as
     * backfillProductsOntoDefaultEnterprise() just above. Every pre-existing
     * order's items all came from the single pre-marketplace catalog, so
     * this is unambiguous: there is only one enterprise they could belong to.
     */
    private void backfillOrdersOntoDefaultEnterprise() {
        Enterprise platformDefault = enterpriseRepository.findByPlatformDefaultTrue().orElse(null);
        if (platformDefault == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Order order : orderRepository.findByEnterpriseIsNull()) {
                order.setEnterprise(platformDefault);
                orderRepository.save(order);
            }
        });
    }

    /**
     * Same backfill, for PosSale.enterprise — every pre-existing till sale
     * predates per-seller POS scoping, so (same reasoning as orders above)
     * there's only one enterprise its items could have come from.
     */
    private void backfillPosSalesOntoDefaultEnterprise() {
        Enterprise platformDefault = enterpriseRepository.findByPlatformDefaultTrue().orElse(null);
        if (platformDefault == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (PosSale sale : posSaleRepository.findByEnterpriseIsNull()) {
                sale.setEnterprise(platformDefault);
                posSaleRepository.save(sale);
            }
        });
    }

    private void saveBackfillVariant(Product product, String colorName, String size, int quantity, boolean untracked) {
        productVariantRepository.save(ProductVariant.builder()
                .product(product)
                .colorName(colorName)
                .size(size)
                .quantity(quantity)
                .untracked(untracked)
                .build());
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
     * Role.name used to be @Column(unique = true) on its own — now it's part
     * of a composite (enterprise_id, name) unique constraint instead (see
     * Role.java), so two different enterprises can each have an "Owner"
     * role. ddl-auto=update only ADDS missing constraints, it never drops or
     * alters an existing one, so the original single-column unique index
     * (confirmed in a running install as "ukofx66keruapi6vyqpv6f2or37" —
     * Hibernate's deterministic auto-generated name for this exact
     * table+column pairing, so this should match any install created by
     * this same entity history) would otherwise keep blocking same-named
     * roles across enterprises forever. Runs every boot, no-op once gone —
     * same pattern as dropStaleEnumCheckConstraints() above.
     */
    private void dropStaleRoleNameUniqueConstraint() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE roles DROP CONSTRAINT IF EXISTS ukofx66keruapi6vyqpv6f2or37");
        } catch (SQLException e) {
            log.warn("Could not drop stale roles.name unique constraint (harmless if it was already gone)", e);
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
     * The "Super Admin" role is seeded once, on an empty DB, with whatever
     * {@code Permission.values()} held at that moment — a permission added
     * later (like {@link Permission#MANAGE_DELIVERY_AGENTS}) never reaches
     * an already-existing installation's Super Admin role on its own, and
     * the frontend's {@code isSuperAdmin()} check (has every known
     * permission) would then start reporting the real super admin as not
     * one. Runs on every boot; a no-op once the role already has everything.
     */
    private void backfillSuperAdminHasAllPermissions() {
        roleRepository.findAll().stream()
                .filter(role -> "Super Admin".equals(role.getName()))
                .forEach(role -> {
                    Set<Permission> permissions = new HashSet<>(role.getPermissions());
                    if (permissions.addAll(Set.of(Permission.values()))) {
                        role.setPermissions(permissions);
                        roleRepository.save(role);
                        log.info("Backfilled missing permissions onto Super Admin role");
                    }
                });
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

    /** The delivery team's own numbers, used from day one — editable later from Delivery Agents. */
    private void seedDeliveryContacts() {
        if (deliveryContactRepository.count() > 0) return;
        deliveryContactRepository.save(DeliveryContact.builder().name("Delivery Agent 1").phone("673019577").position(0).build());
        deliveryContactRepository.save(DeliveryContact.builder().name("Delivery Agent 2").phone("650723428").position(1).build());
        deliveryContactRepository.save(DeliveryContact.builder().name("Delivery Agent 3").phone("655233862").position(2).build());
        log.info("Seeded default delivery contacts");
    }
}
