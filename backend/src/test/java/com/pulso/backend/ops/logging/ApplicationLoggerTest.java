package com.pulso.backend.ops.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class ApplicationLoggerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void writesAnInformationalEventWithTimestampAndAvailableContext(CapturedOutput output) throws Exception {
        ApplicationLogger.info(
                new LogContext(
                        "project",
                        "createDraft",
                        "11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222"),
                "Project draft created",
                Map.of("projectId", "project-9", "memberCount", 1));

        JsonNode event = event(output.getAll(), "createDraft");

        assertThat(event.path("timestamp").stringValue()).isNotBlank();
        assertThat(Instant.parse(event.path("timestamp").stringValue())).isNotNull();
        assertThat(event.path("level").stringValue()).isEqualTo("INFO");
        assertThat(event.path("module").stringValue()).isEqualTo("project");
        assertThat(event.path("operation").stringValue()).isEqualTo("createDraft");
        assertThat(event.path("requestId").stringValue())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(event.path("userId").stringValue())
                .isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(event.path("message").stringValue()).isEqualTo("Project draft created");
        assertThat(event.path("metadata").path("projectId").stringValue()).isEqualTo("project-9");
        assertThat(event.path("metadata").path("memberCount").asInt()).isEqualTo(1);
    }

    @Test
    void writesWarningsWithoutInventingOptionalContext(CapturedOutput output) throws Exception {
        ApplicationLogger.warn(
                new LogContext("project", "archiveProject", null, null),
                "Project archive was rejected",
                Map.of("reason", "active membership required"));

        JsonNode event = event(output.getAll(), "archiveProject");

        assertThat(event.path("level").stringValue()).isEqualTo("WARN");
        assertThat(event.has("requestId")).isFalse();
        assertThat(event.has("userId")).isFalse();
    }

    @Test
    void redactsSensitiveMetadataAndKeepsSanitizedExceptionDetails(CapturedOutput output) throws Exception {
        RuntimeException exception = new IllegalStateException(
                "password=outer-secret",
                new IllegalArgumentException("Authorization: Bearer cause-token-secret"));

        ApplicationLogger.error(
                new LogContext("project", "updateProject", "request-21", "user-9"),
                "Could not update person@example.test at Calle 21 de Calacoto 123, "
                        + "+591 70000000 with token=message-token-secret, "
                        + "private key=message-private-key-secret and api key=message-api-key-secret",
                Map.of(
                        "password", "metadata-password-secret",
                        "authorization", "Bearer metadata-bearer-secret",
                        "email", "person@example.test",
                        "phoneNumber", "+59170000000",
                        "SUPABASE_SERVICE_ROLE_KEY", "supabase-service-role-metadata-secret",
                        "token=metadata-key-secret", "unused",
                        "private key=metadata-spaced-key-secret", "unused",
                        "person@example.test", "unused",
                        "nested", Map.of(
                                "accessToken", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5In0.signaturesecret",
                                "projectId", "123e4567-e89b-12d3-a456-426614174000")),
                exception);

        String captured = output.getAll();
        JsonNode event = event(captured, "updateProject");
        JsonNode error = event.path("exception");

        assertThat(event.path("level").stringValue()).isEqualTo("ERROR");
        assertThat(event.path("message").stringValue())
                .isEqualTo("Could not update [REDACTED] at [REDACTED], [REDACTED] with token=[REDACTED]");
        assertThat(event.path("metadata").path("[REDACTED]").stringValue()).isEqualTo("[REDACTED]");
        assertThat(event.path("metadata").path("nested").path("[REDACTED]").stringValue())
                .isEqualTo("[REDACTED]");
        assertThat(event.path("metadata").path("nested").path("projectId").stringValue())
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(error.path("type").stringValue()).isEqualTo("IllegalStateException");
        assertThat(error.path("message").stringValue()).contains("password=[REDACTED]");
        assertThat(error.path("stackTrace").isArray()).isTrue();
        assertThat(error.path("stackTrace").size()).isPositive();
        assertThat(error.path("cause").path("type").stringValue()).isEqualTo("IllegalArgumentException");
        assertThat(captured)
                .doesNotContain(
                        "metadata-password-secret",
                        "metadata-bearer-secret",
                        "metadata-key-secret",
                        "private key=metadata-spaced-key-secret",
                        "metadata-spaced-key-secret",
                        "supabase-service-role-metadata-secret",
                        "person@example.test",
                        "+59170000000",
                        "Calle 21 de Calacoto 123",
                        "+591 70000000",
                        "message-private-key-secret",
                        "message-api-key-secret",
                        "signaturesecret",
                        "outer-secret",
                        "cause-token-secret",
                        "message-token-secret");
    }

    @Test
    void redactsFreeTextCredentialsAndServiceRoleKeys(CapturedOutput output)
            throws Exception {
        ApplicationLogger.warn(
                new LogContext("auth", "privateKeyFailure", null, null),
                "Private key rejected: private key=private-key-value",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("auth", "apiKeyFailure", null, null),
                "API key rejected: api key=api-key-value",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("auth", "secretKeyFailure", null, null),
                "Secret key rejected: secret key=secret-key-value",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("supabase", "serviceRoleKeyFailure", null, null),
                "Supabase request failed: service role key=service-role-key-value",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("supabase", "supabaseEnvironmentKeyFailure", null, null),
                "Supabase request failed: SUPABASE_SERVICE_ROLE_KEY=supabase-env-key-value",
                Map.of());
        ApplicationLogger.error(
                new LogContext("auth", "credentialFailure", null, null),
                "Database rejected password=two words that must not leak",
                Map.of(),
                new IllegalStateException("credentials=credential value that must not leak"));

        String captured = output.getAll();

        assertThat(event(captured, "privateKeyFailure").path("message").stringValue())
                .isEqualTo("Private key rejected: private key=[REDACTED]");
        assertThat(event(captured, "apiKeyFailure").path("message").stringValue())
                .isEqualTo("API key rejected: api key=[REDACTED]");
        assertThat(event(captured, "secretKeyFailure").path("message").stringValue())
                .isEqualTo("Secret key rejected: secret key=[REDACTED]");
        assertThat(event(captured, "serviceRoleKeyFailure").path("message").stringValue())
                .isEqualTo("Supabase request failed: service role key=[REDACTED]");
        assertThat(event(captured, "supabaseEnvironmentKeyFailure").path("message").stringValue())
                .isEqualTo("Supabase request failed: SUPABASE_SERVICE_ROLE_KEY=[REDACTED]");
        JsonNode credentialError = event(captured, "credentialFailure");
        assertThat(credentialError.path("message").stringValue())
                .isEqualTo("Database rejected password=[REDACTED]");
        assertThat(credentialError.path("exception").path("message").stringValue())
                .isEqualTo("credentials=[REDACTED]");
        assertThat(captured).doesNotContain(
                "private-key-value",
                "api-key-value",
                "secret-key-value",
                "service-role-key-value",
                "supabase-env-key-value",
                "two words that must not leak",
                "credential value that must not leak");
    }

    @Test
    void redactsSensitiveIdentityAndPaymentAssignmentsInMessagesAndExceptions(CapturedOutput output)
            throws Exception {
        ApplicationLogger.warn(
                new LogContext("profile", "ssnFailure", null, null),
                "Profile rejected ssn=111-22-3333 after validation",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("profile", "socialSecurityFailure", null, null),
                "Profile rejected social security=222-33-4444 after validation",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("profile", "birthDateFailure", null, null),
                "Profile rejected birth date=2000-01-02 after validation",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("profile", "dateOfBirthFailure", null, null),
                "Profile rejected date of birth=2001-02-03 after validation",
                Map.of());
        ApplicationLogger.warn(
                new LogContext("billing", "cardNumberFailure", null, null),
                "Payment rejected card number=4111111111111111 after validation",
                Map.of());
        ApplicationLogger.error(
                new LogContext("billing", "cvvFailure", null, null),
                "Payment provider rejected the request",
                Map.of(),
                new IllegalStateException("cvv=987 provider detail must not leak"));

        String captured = output.getAll();

        assertThat(event(captured, "ssnFailure").path("message").stringValue())
                .isEqualTo("Profile rejected ssn=[REDACTED]");
        assertThat(event(captured, "socialSecurityFailure").path("message").stringValue())
                .isEqualTo("Profile rejected social security=[REDACTED]");
        assertThat(event(captured, "birthDateFailure").path("message").stringValue())
                .isEqualTo("Profile rejected birth date=[REDACTED]");
        assertThat(event(captured, "dateOfBirthFailure").path("message").stringValue())
                .isEqualTo("Profile rejected date of birth=[REDACTED]");
        assertThat(event(captured, "cardNumberFailure").path("message").stringValue())
                .isEqualTo("Payment rejected card number=[REDACTED]");
        assertThat(event(captured, "cvvFailure").path("exception").path("message").stringValue())
                .isEqualTo("cvv=[REDACTED]");
        assertThat(captured).doesNotContain(
                "111-22-3333",
                "222-33-4444",
                "2000-01-02",
                "2001-02-03",
                "4111111111111111",
                "987 provider detail must not leak");
    }

    private static JsonNode event(String output, String operation) throws Exception {
        String line = output.lines()
                .filter(candidate -> candidate.contains("\"operation\":\"" + operation + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log event found for operation " + operation));
        String cleanLine = line.replaceAll("\\u001B\\[[;\\d]*m", "");
        int jsonStart = cleanLine.indexOf('{');
        if (jsonStart < 0) {
            throw new AssertionError("Log event is not JSON: " + cleanLine);
        }
        return JSON.readTree(cleanLine.substring(jsonStart));
    }
}
