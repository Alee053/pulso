package com.pulso.backend.connection.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.connection.application.ConnectionIntentRepository;
import com.pulso.backend.connection.application.CreateConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;
import com.pulso.backend.notification.application.RecordingNotificationPublisher;

@WebMvcTest(ConnectionIntentController.class)
@Import({ SecurityConfig.class, ConnectionIntentSecurityTest.TestBeans.class })
class ConnectionIntentSecurityTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final UUID PROJECT_ID = id("22222222-2222-2222-2222-222222222222");
    private static final UUID SENDER_ID = id("11111111-1111-1111-1111-111111111111");
    private static final UUID SPOOFED_ID = id("99999999-9999-9999-9999-999999999999");
    private static final String PATH = "/api/v1/projects/" + PROJECT_ID + "/connection-intents";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingRepository repository;

    @BeforeEach
    void resetRepository() {
        repository.callCount = 0;
        repository.senderProfileId = null;
    }

    @Test
    void requiresAValidAuthenticatedAccessToken() throws Exception {
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH)
                .header("Authorization", "Bearer not-a-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH)
                .header("Authorization", "Bearer " + token("service_role", SENDER_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
        for (String subject : new String[] {
                "not-a-profile-uuid",
                "1-1-1-1-1",
                "+1-1-1-1-1"
        }) {
            mockMvc.perform(post(PATH)
                    .header("Authorization", "Bearer " + token("authenticated", subject))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest()))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(repository.callCount).isZero();
    }

    @Test
    void derivesSenderFromTheValidatedJwtSubject() throws Exception {
        mockMvc.perform(post(PATH)
                .header("Authorization", "Bearer " + token("authenticated", SENDER_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderProfileId").value(SENDER_ID.toString()));

        assertThat(repository.callCount).isEqualTo(1);
        assertThat(repository.senderProfileId).isEqualTo(SENDER_ID);
    }

    @Test
    void rejectsSenderAndStatusSpoofingBeforePersistence() throws Exception {
        String senderSpoof = "{\"reason\":\"OTHER\",\"senderProfileId\":\"" + SPOOFED_ID + "\"}";
        String statusSpoof = "{\"reason\":\"OTHER\",\"status\":\"ACCEPTED\"}";
        for (String body : new String[] { senderSpoof, statusSpoof }) {
            mockMvc.perform(post(PATH)
                    .header("Authorization", "Bearer " + token("authenticated", SENDER_ID.toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        assertThat(repository.callCount).isZero();
    }

    private static String token(String role, String subject) throws Exception {
        Date issuedAt = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .audience("authenticated")
                .claim("role", role)
                .issueTime(issuedAt)
                .expirationTime(new Date(issuedAt.getTime() + 300_000))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(new SecretKeySpec(
                "test-secret-that-is-long-enough-for-local-tests".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256")));
        return signedJwt.serialize();
    }

    private static String validRequest() {
        return "{\"reason\":\"COLLABORATE\"}";
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CapturingRepository connectionIntentRepository() {
            return new CapturingRepository();
        }

        @Bean
        CreateConnectionIntent createConnectionIntent(CapturingRepository repository) {
            return new CreateConnectionIntent(repository, new RecordingNotificationPublisher());
        }
    }

    static final class CapturingRepository implements ConnectionIntentRepository {

        private int callCount;
        private UUID senderProfileId;

        @Override
        public ConnectionIntent createPending(
                UUID senderId,
                UUID projectId,
                UUID opportunityId,
                ConnectionIntentReason reason,
                String message) {
            callCount++;
            senderProfileId = senderId;
            return ConnectionIntent.pending(
                    id("44444444-4444-4444-4444-444444444444"),
                    senderId,
                    projectId,
                    opportunityId,
                    reason,
                    message,
                    OffsetDateTime.now());
        }
    }
}
