package com.pulso.backend.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    @Test
    void logsSanitizedDataStoreExceptionsAndKeepsThePublicProblemResponse(CapturedOutput output) {
        DataAccessResourceFailureException exception =
                new DataAccessResourceFailureException("password=database-password-secret");

        ProblemDetail result = new ApiExceptionHandler().dataStoreUnavailable(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(result.getTitle()).isNotBlank();
        assertThat(output.getAll()).contains("\"operation\":\"dataStoreUnavailable\"");
        assertThat(output.getAll()).contains("DataAccessResourceFailureException");
        assertThat(output.getAll()).doesNotContain("database-password-secret");
    }
}
