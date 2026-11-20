package com.pulso.backend.report.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.api.ProjectReviewController;
import com.pulso.backend.project.application.ProjectReviewRepository;
import com.pulso.backend.project.application.ReviewProjects;
import com.pulso.backend.report.application.CreateReport;
import com.pulso.backend.report.application.ReportRepository;
import com.pulso.backend.report.domain.ReportReceipt;
import com.pulso.backend.report.domain.ReportTarget;

@WebMvcTest({ReportController.class, ProjectReviewController.class})
@Import({SecurityConfig.class, CreateReport.class, ReviewProjects.class})
class ReportControllerTest {
    private static final String PATH = "/api/v1/reports";
    private static final UUID REPORTER = new UUID(0, 1);
    private static final UUID TARGET = new UUID(0, 2);
    private static final UUID RECEIPT = new UUID(0, 3);

    @Autowired private MockMvc mvc;
    @MockitoBean private ReportRepository repository;
    @MockitoBean private ProjectReviewRepository projectReviews;

    @ParameterizedTest
    @ValueSource(strings = {"targetProfileId", "targetProjectId", "targetMessageId"})
    void createsAnOpenReportWithOnlyAReceipt(String field) throws Exception {
        given(repository.insertIfAccessible(eq(REPORTER), any(), eq("ABUSE"), isNull()))
                .willReturn(Optional.of(new ReportReceipt(RECEIPT, "OPEN", Instant.EPOCH)));

        mvc.perform(post(PATH).with(jwt().jwt(token -> token.subject(REPORTER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"%s":"%s","reason":" ABUSE ","reporterProfileId":"%s","status":"RESOLVED"}
                                """.formatted(field, TARGET, TARGET)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RECEIPT.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.reporterProfileId").doesNotExist())
                .andExpect(jsonPath("$.resolutionNote").doesNotExist());

        verify(repository).insertIfAccessible(REPORTER, new ReportTarget(
                field.equals("targetProfileId") ? TARGET : null,
                field.equals("targetProjectId") ? TARGET : null,
                field.equals("targetMessageId") ? TARGET : null), "ABUSE", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"reason\":\"ABUSE\"}",
        "{\"reason\":\"  \",\"targetProfileId\":\"%1$s\"}",
        "{\"reason\":\"ABUSE\",\"targetProfileId\":\"%1$s\",\"targetProjectId\":\"%1$s\"}",
        "{\"reason\":\"ABUSE\",\"targetProfileId\":\"%1$s\",\"targetMessageId\":\"%1$s\"}",
        "{\"reason\":\"ABUSE\",\"targetProjectId\":\"%1$s\",\"targetMessageId\":\"%1$s\"}",
        "{\"reason\":\"ABUSE\",\"targetProfileId\":\"%1$s\",\"targetProjectId\":\"%1$s\",\"targetMessageId\":\"%1$s\"}"
    })
    void rejectsInvalidInputBeforePersistence(String body) throws Exception {
        mvc.perform(post(PATH).with(jwt().jwt(token -> token.subject(REPORTER.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(TARGET)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingAndInvalidAuthenticationAndDisallowsReportReads() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\":\"%s\",\"reason\":\"ABUSE\"}".formatted(TARGET)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void returnsGenericNotFoundForUnavailableTargets() throws Exception {
        mvc.perform(post(PATH).with(jwt().jwt(token -> token.subject(REPORTER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMessageId\":\"%s\",\"reason\":\"ABUSE\"}".formatted(TARGET)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Report target is unavailable."));
    }

    @Test
    void creationDoesNotGrantAccessToTheExistingProjectReviewQueue() throws Exception {
        given(repository.insertIfAccessible(eq(REPORTER), any(), eq("ABUSE"), isNull()))
                .willReturn(Optional.of(new ReportReceipt(RECEIPT, "OPEN", Instant.EPOCH)));
        var reporter = jwt().jwt(token -> token.subject(REPORTER.toString()).claim("platformRole", "ADMIN"));

        mvc.perform(post(PATH).with(reporter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\":\"%s\",\"reason\":\"ABUSE\"}".formatted(TARGET)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/project-reviews").with(reporter))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("project_review_forbidden"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(projectReviews).isActiveReviewer(REPORTER);
        verifyNoMoreInteractions(projectReviews);
    }
}
