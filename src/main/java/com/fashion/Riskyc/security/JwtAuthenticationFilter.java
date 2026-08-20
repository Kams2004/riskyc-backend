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
                AdminPrincipal principal = jwtService.parse(token);
                List<GrantedAuthority> authorities = principal.permissions().stream()
                        .map(Permission::name)
                        .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(PERMISSION_AUTHORITY_PREFIX + name))
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Rejected invalid admin JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
