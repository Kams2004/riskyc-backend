package com.fashion.Riskyc.security;

import com.fashion.Riskyc.config.JwtProperties;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Permission;
import com.fashion.Riskyc.service.RoleService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and validates the JWT that backs an admin session. The token
 * carries the admin's role and permission set directly in its claims so a
 * request can be authorized without a database round trip on every call.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE_ID = "roleId";
    private static final String CLAIM_ROLE_NAME = "roleName";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_ENTERPRISE_ID = "enterpriseId";
    /** Absent on admin tokens (including every one issued before customer tokens existed); "customer" otherwise. */
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_CUSTOMER = "customer";
    /** Customers stay signed in on their phone/browser; the token only unlocks their own orders and chat. */
    private static final long CUSTOMER_TOKEN_DAYS = 90;

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(AdminUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getExpirationMinutes(), ChronoUnit.MINUTES);

        List<String> permissionNames = RoleService.effectivePermissions(user).stream()
                .map(Enum::name)
                .toList();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getFirstName() + " " + user.getLastName())
                .claim(CLAIM_ROLE_ID, user.getRole().getId().toString())
                .claim(CLAIM_ROLE_NAME, user.getRole().getName())
                .claim(CLAIM_PERMISSIONS, permissionNames)
                .claim(CLAIM_ENTERPRISE_ID, user.getEnterprise() != null ? user.getEnterprise().getId().toString() : null)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    /** Parses and validates the token, returning the authenticated admin principal. Throws {@link JwtException} if invalid/expired. */
    public AdminPrincipal parse(String token) {
        Claims claims = claims(token);
        if (TYPE_CUSTOMER.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("A customer token is not an admin token");
        }

        @SuppressWarnings("unchecked")
        List<String> permissionNames = claims.get(CLAIM_PERMISSIONS, List.class);
        Set<Permission> permissions = permissionNames == null
                ? Set.of()
                : permissionNames.stream().map(Permission::valueOf).collect(Collectors.toUnmodifiableSet());

        return new AdminPrincipal(
                claims.getSubject(),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_ROLE_ID, String.class),
                claims.get(CLAIM_ROLE_NAME, String.class),
                permissions,
                claims.get(CLAIM_ENTERPRISE_ID, String.class)
        );
    }

    public String generateCustomerToken(UUID customerId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(customerId.toString())
                .claim(CLAIM_TYPE, TYPE_CUSTOMER)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(CUSTOMER_TOKEN_DAYS, ChronoUnit.DAYS)))
                .signWith(signingKey())
                .compact();
    }

    /** The customer id in a valid customer token. Throws {@link JwtException} for an admin token or an invalid/expired one. */
    public UUID parseCustomer(String token) {
        Claims claims = claims(token);
        if (!TYPE_CUSTOMER.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not a customer token");
        }
        return UUID.fromString(claims.getSubject());
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
