package com.fashion.Riskyc.security;

import com.fashion.Riskyc.entity.Permission;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <token>}, validates it, and — if
 * valid — populates the {@link org.springframework.security.core.context.SecurityContext}
 * with an {@link AdminPrincipal} and one {@link GrantedAuthority} per
 * permission (as {@code PERM_<NAME>}), so {@code hasAuthority(...)} rules in
 * {@code SecurityConfig} can gate individual endpoints.
 *
 * <p>An absent or invalid token simply leaves the request unauthenticated —
 * public endpoints still work, protected ones fall through to a 403 from
 * Spring Security's default handling.
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String PERMISSION_AUTHORITY_PREFIX = "PERM_";
    /** Every admin token carries this — the rules for "any admin" (and not a customer or a till) require it. */
    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Object parsed = jwtService.parseAny(token);
                if (parsed instanceof AdminPrincipal principal) {
                    List<GrantedAuthority> authorities = principal.permissions().stream()
                            .map(Permission::name)
                            .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(PERMISSION_AUTHORITY_PREFIX + name))
                            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                    authorities.add(new SimpleGrantedAuthority(ADMIN_AUTHORITY));
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
                } else if (parsed instanceof CustomerPrincipal customer) {
                    // Proves only who the customer is — see CustomerAccess.
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(customer, null,
                            List.of(new SimpleGrantedAuthority(CustomerPrincipal.AUTHORITY))));
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Rejected invalid JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
