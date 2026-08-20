package com.fashion.Riskyc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Base64-encoded HMAC-SHA256 signing key. Must be overridden via
     * {@code JWT_SECRET} in any environment beyond local dev — the default
     * here is a fixed dev-only value so the app runs out of the box.
     */
    private String secret = "cmlza3ljLWZhc2hpb24tZGV2LW9ubHktand0LXNpZ25pbmcta2V5LWNoYW5nZS1pbi1wcm9k";

    private long expirationMinutes = 480; // 8h admin session
}
