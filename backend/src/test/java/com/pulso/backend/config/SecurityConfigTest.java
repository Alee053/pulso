package com.pulso.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class SecurityConfigTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";

    @Test
    void acceptsAuthenticatedUserTokens() throws Exception {
        JwtDecoder decoder = decoder();

        assertThat(decoder.decode(token("authenticated")).getClaimAsString("role"))
                .isEqualTo("authenticated");
    }

    @Test
    void rejectsServiceRoleTokensForUserApiAccess() throws Exception {
        JwtDecoder decoder = decoder();

        assertThatThrownBy(() -> decoder.decode(token("service_role")))
                .hasMessageContaining("JWT role is not accepted");
    }

    @Test
    void rejectsAmbiguousVerificationConfiguration() {
        assertThatThrownBy(() -> new SecurityConfig().supabaseJwtDecoder(
                new SupabaseJwtProperties(ISSUER, "http://jwks.test", SECRET, "authenticated")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void allowsOnlyConfiguredCorsOrigin() {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource("http://localhost:4200");
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/v1/startups"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("http://localhost:4200"))
                .isEqualTo("http://localhost:4200");
        assertThat(configuration.checkOrigin("https://untrusted.example"))
                .isNull();
    }

    private static JwtDecoder decoder() {
        return new SecurityConfig().supabaseJwtDecoder(
                new SupabaseJwtProperties(ISSUER, null, SECRET, "authenticated"));
    }

    private static String token(String role) throws Exception {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + 300_000);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("11111111-1111-1111-1111-111111111111")
                .audience("authenticated")
                .claim("role", role)
                .issueTime(issuedAt)
                .expirationTime(expiresAt)
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256")));
        return signedJwt.serialize();
    }
}
