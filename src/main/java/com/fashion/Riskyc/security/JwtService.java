package com.fashion.Riskyc.security;

import com.fashion.Riskyc.config.JwtProperties;
import com.fashion.Riskyc.entity.AdminUser;
import com.fashion.Riskyc.entity.Permission;
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
    private static final String CLAIM_ROLE_ID = "roleId";
    private static final String CLAIM_ROLE_NAME = "roleName";
    private static final String CLAIM_PERMISSIONS = "permissions";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(AdminUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getExpirationMinutes(), ChronoUnit.MINUTES);

        List<String> permissionNames = user.getRole().getPermissions().stream()
                .map(Enum::name)
                .toList();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE_ID, user.getRole().getId().toString())
                .claim(CLAIM_ROLE_NAME, user.getRole().getName())
                .claim(CLAIM_PERMISSIONS, permissionNames)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    /** Parses and validates the token, returning the authenticated admin principal. Throws {@link JwtException} if invalid/expired. */
    public AdminPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        @SuppressWarnings("unchecked")
        List<String> permissionNames = claims.get(CLAIM_PERMISSIONS, List.class);
        Set<Permission> permissions = permissionNames == null
                ? Set.of()
                : permissionNames.stream().map(Permission::valueOf).collect(Collectors.toUnmodifiableSet());

        return new AdminPrincipal(
                claims.getSubject(),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_ROLE_ID, String.class),
                claims.get(CLAIM_ROLE_NAME, String.class),
                permissions
        );
    }
}
