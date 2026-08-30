package com.fashion.Riskyc.config;

import com.fashion.Riskyc.security.JwtAuthenticationFilter;
import com.fashion.Riskyc.security.JwtService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

/**
 * Every write/admin-only endpoint requires a valid admin JWT
 * ({@link JwtAuthenticationFilter}) carrying the specific
 * {@code PERM_<PERMISSION>} authority for that action — the same
 * permission set the admin panel uses to decide what to show. Storefront
 * reads and the customer checkout/chat flow stay public since no customer
 * auth scheme was requested.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    private static final String P_VIEW_PRODUCTS = "PERM_VIEW_PRODUCTS";
    private static final String P_MANAGE_PRODUCTS = "PERM_MANAGE_PRODUCTS";
    private static final String P_VIEW_CATEGORIES = "PERM_VIEW_CATEGORIES";
    private static final String P_MANAGE_CATEGORIES = "PERM_MANAGE_CATEGORIES";
    private static final String P_VIEW_ORDERS = "PERM_VIEW_ORDERS";
    private static final String P_MANAGE_ORDERS = "PERM_MANAGE_ORDERS";
    private static final String P_MANAGE_TREATMENT = "PERM_MANAGE_TREATMENT";
    private static final String P_VIEW_CUSTOMERS = "PERM_VIEW_CUSTOMERS";
    private static final String P_MANAGE_CUSTOMERS = "PERM_MANAGE_CUSTOMERS";
    private static final String P_VIEW_USERS = "PERM_VIEW_USERS";
    private static final String P_MANAGE_USERS = "PERM_MANAGE_USERS";
    private static final String P_VIEW_CHAT = "PERM_VIEW_CHAT";
    private static final String P_MANAGE_CHAT = "PERM_MANAGE_CHAT";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Spring Security's filter chain runs before the DispatcherServlet,
                // so it must be told about CORS explicitly — otherwise it blocks the
                // browser's preflight OPTIONS request before WebConfig's
                // WebMvcConfigurer CORS mapping (which only applies inside MVC) ever
                // sees it. `Customizer.withDefaults()` makes Security delegate to the
                // same CorsConfigurationSource Spring MVC already resolved from
                // WebConfig, so there's only one CORS policy to keep in sync.
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonEntryPoint(HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(res.getWriter(), Map.of(
                                    "status", 403, "error", "Forbidden",
                                    "message", "You don't have permission to do this."
                            ));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // ── Always public ──
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin-users/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/customers/register", "/api/customers/login").permitAll()

                        // NOTE: these more specific admin-only rules MUST be declared before
                        // the generic public GET rules below — Spring Security uses
                        // first-match-wins, and "/api/products/{id}" would otherwise also
                        // match "/api/products/admin" (a path variable matches any segment,
                        // including the literal "admin") and make it public by accident.
                        .requestMatchers(HttpMethod.GET, "/api/products/admin").hasAuthority(P_VIEW_PRODUCTS)
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasAuthority(P_MANAGE_PRODUCTS)
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAuthority(P_MANAGE_PRODUCTS)
                        .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasAuthority(P_MANAGE_PRODUCTS)
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**", "/api/media/**").hasAuthority(P_MANAGE_PRODUCTS)

                        // ── Always public (storefront + checkout + chat) ──
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        // GET and HEAD both need to be public here — link-preview crawlers
                        // (WhatsApp, Facebook, etc.) probe an og:image URL with HEAD before
                        // fetching it with GET, and a 401 on the HEAD probe makes some of
                        // them give up before ever issuing the GET.
                        .requestMatchers(HttpMethod.GET, "/api/media/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/media/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/payment-method", "/api/orders/*/payment-proof").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/{id}", "/api/orders/customer/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/conversations", "/api/conversations/messages", "/api/conversations/*/messages/image", "/api/conversations/*/messages/voice").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/conversations/customer/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/conversations/{id}").permitAll()
                        .requestMatchers("/api/push/**").permitAll()

                        // ── Categories ──
                        .requestMatchers(HttpMethod.POST, "/api/categories/**").hasAuthority(P_MANAGE_CATEGORIES)
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasAuthority(P_MANAGE_CATEGORIES)
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasAuthority(P_MANAGE_CATEGORIES)

                        // ── Orders (admin desk) ──
                        .requestMatchers(HttpMethod.GET, "/api/orders").hasAuthority(P_VIEW_ORDERS)
                        // Packaging (Treatment) has its own permission — declared before the
                        // generic PATCH /api/orders/** rule below, which would otherwise also
                        // match these and gate them on MANAGE_ORDERS instead.
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/packaging/start", "/api/orders/*/packaging/complete")
                            .hasAuthority(P_MANAGE_TREATMENT)
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/**").hasAuthority(P_MANAGE_ORDERS)

                        // ── Customers (admin management) ──
                        .requestMatchers(HttpMethod.GET, "/api/customers").hasAuthority(P_VIEW_CUSTOMERS)
                        .requestMatchers(HttpMethod.PATCH, "/api/customers/**").hasAuthority(P_MANAGE_CUSTOMERS)
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/**").hasAuthority(P_MANAGE_CUSTOMERS)

                        // ── Admin users & roles ──
                        .requestMatchers(HttpMethod.GET, "/api/admin-users/**", "/api/roles/**").hasAuthority(P_VIEW_USERS)
                        .requestMatchers(HttpMethod.POST, "/api/admin-users/**", "/api/roles/**").hasAuthority(P_MANAGE_USERS)
                        .requestMatchers(HttpMethod.PUT, "/api/admin-users/**", "/api/roles/**").hasAuthority(P_MANAGE_USERS)
                        .requestMatchers(HttpMethod.DELETE, "/api/admin-users/**", "/api/roles/**").hasAuthority(P_MANAGE_USERS)

                        // ── Chat (admin inbox) ──
                        .requestMatchers(HttpMethod.GET, "/api/conversations").hasAuthority(P_VIEW_CHAT)
                        .requestMatchers(HttpMethod.POST, "/api/conversations/*/read").hasAuthority(P_VIEW_CHAT)
                        .requestMatchers(HttpMethod.DELETE, "/api/conversations/**").hasAuthority(P_MANAGE_CHAT)

                        // ── Notifications: any authenticated admin ──
                        .requestMatchers("/api/notifications/admin/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint jsonEntryPoint(HttpStatus status, String message) {
        return (request, response, authException) -> {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "status", status.value(), "error", status.getReasonPhrase(), "message", message
            ));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
