package com.fashion.Riskyc.security;

import com.fashion.Riskyc.service.PosDeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a till from {@code X-Device-Token: <deviceId>.<secret>}. It only ever grants ROLE_POS_DEVICE,
 * which unlocks the /api/pos/device/** routes and nothing an admin permission guards.
 */
@RequiredArgsConstructor
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Device-Token";
    public static final String AUTHORITY = "ROLE_POS_DEVICE";

    private final PosDeviceService posDeviceService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null && !token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            posDeviceService.authenticate(token).ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(AUTHORITY)))));
        }
        filterChain.doFilter(request, response);
    }
}
