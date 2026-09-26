package com.pulso.backend.catalog.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulso.backend.catalog.application.ActiveCatalogRepository;
import com.pulso.backend.catalog.application.ListActiveCatalogs;
import com.pulso.backend.catalog.domain.SectorCatalogItem;
import com.pulso.backend.catalog.domain.SkillCatalogItem;
import com.pulso.backend.catalog.domain.TagCatalogItem;
import com.pulso.backend.config.CatalogProperties;
import com.pulso.backend.config.SecurityConfig;

@WebMvcTest(CatalogController.class)
@Import({ SecurityConfig.class, CatalogSecurityTest.TestBeans.class })
class CatalogSecurityTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedUserToken() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(get("/api/v1/catalogs").with(jwt().jwt(jwt)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAServiceRoleToken() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs")
                .header("Authorization", "Bearer " + token("service_role")))
                .andExpect(status().isUnauthorized());
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        ActiveCatalogRepository activeCatalogRepository() {
            return new ActiveCatalogRepository() {
                @Override
                public List<SectorCatalogItem> findActiveSectors(int limit) {
                    return List.of();
                }

                @Override
                public List<TagCatalogItem> findActiveTags(int limit) {
                    return List.of();
                }

                @Override
                public List<SkillCatalogItem> findActiveSkills(int limit) {
                    return List.of();
                }
            };
        }

        @Bean
        ListActiveCatalogs listActiveCatalogs(ActiveCatalogRepository repository) {
            return new ListActiveCatalogs(repository, new CatalogProperties(500));
        }
    }
}
