package com.pulso.backend.report.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.report.application.CreateReport;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final CreateReport createReport;

    public ReportController(CreateReport createReport) {
        this.createReport = createReport;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateReportRequest request) {
        return ReportResponse.from(createReport.execute(
                authenticatedProfileId(jwt), request.target(), request.reason(), request.description()));
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();
        if (subject != null) {
            try {
                UUID profileId = UUID.fromString(subject);
                if (profileId.toString().equalsIgnoreCase(subject)) {
                    return profileId;
                }
            } catch (IllegalArgumentException exception) {
                throw new InvalidAuthenticatedSubjectException();
            }
        }
        throw new InvalidAuthenticatedSubjectException();
    }
}
