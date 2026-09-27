package com.pulso.backend.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({
        SupabaseJwtProperties.class,
        DiscoveryProperties.class,
        CatalogProperties.class,
        PreferenceProperties.class,
        ProfileProperties.class,
        ProjectProperties.class
})
public class SecurityConfig {

    private static final String AUTHENTICATED_ROLE = "authenticated";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health",
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/actuator/info")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/startups").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalogs").authenticated()
                        .requestMatchers("/api/v1/me").authenticated()
                        .requestMatchers("/api/v1/me/preferences").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects").authenticated()
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/projects/*/members/*")
                        .authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }));

        return http.build();
    }

    @Bean
    JwtDecoder supabaseJwtDecoder(SupabaseJwtProperties properties) {
        NimbusJwtDecoder decoder;
        boolean hasJwkSetUri = hasText(properties.jwkSetUri());
        boolean hasSecret = hasText(properties.secret());

        if (hasJwkSetUri && hasSecret) {
            throw new IllegalStateException(
                    "Configure exactly one of SUPABASE_JWT_JWK_SET_URI or SUPABASE_JWT_SECRET.");
        }

        if (hasJwkSetUri) {
            decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                    .jwsAlgorithm(SignatureAlgorithm.ES256)
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
        } else if (hasSecret) {
            SecretKey secretKey = new SecretKeySpec(
                    properties.secret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        } else {
            throw new IllegalStateException(
                    "Configure SUPABASE_JWT_JWK_SET_URI for Supabase Auth or "
                            + "SUPABASE_JWT_SECRET for legacy HMAC tokens.");
        }

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = audienceValidator(properties.audience());
        OAuth2TokenValidator<Jwt> roleValidator = authenticatedRoleValidator();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator,
                roleValidator));

        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${pulso.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(SecurityConfig::hasText)
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        if (!hasText(expectedAudience)) {
            throw new IllegalStateException("SUPABASE_JWT_AUDIENCE must not be empty.");
        }

        return jwt -> jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        ApiMessages.JWT_AUDIENCE_REJECTED,
                        null));
    }

    private static OAuth2TokenValidator<Jwt> authenticatedRoleValidator() {
        return jwt -> AUTHENTICATED_ROLE.equals(jwt.getClaimAsString("role"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        ApiMessages.JWT_ROLE_REJECTED,
                        null));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
