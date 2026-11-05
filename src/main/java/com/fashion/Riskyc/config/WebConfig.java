package com.fashion.Riskyc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Allows the Next.js storefront/admin panel (a different origin) to call
 * this API directly from the browser.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * The POS desktop app (Tauri) serves its pages from these fixed origins — tauri://localhost on Linux/macOS,
     * http(s)://tauri.localhost on Windows — so they're allowed on every deployment, independent of the env list.
     */
    private static final String[] DESKTOP_APP_ORIGINS = {"tauri://localhost", "http://tauri.localhost", "https://tauri.localhost"};

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(Stream.concat(Arrays.stream(allowedOrigins), Arrays.stream(DESKTOP_APP_ORIGINS)).toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Range", "Accept-Ranges", "Content-Length", "ETag")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
