package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.security.supabase")
public record SupabaseJwtProperties(
        String issuer,
        String jwkSetUri,
        String secret,
        String audience) {
}
