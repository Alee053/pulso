package com.pulso.backend.project.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectReviewProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.project.application.ReviewProjects;
import com.pulso.backend.project.domain.ProjectReviewCandidate;
import com.pulso.backend.project.domain.ProjectReviewResult;

@RestController
@RequestMapping("/api/v1/admin/project-reviews")
public class ProjectReviewController {
    private final ReviewProjects reviews;
    private final ProjectReviewProperties properties;

    public ProjectReviewController(ReviewProjects reviews, ProjectReviewProperties properties) {
        this.reviews = reviews;
        this.properties = properties;
    }

    @GetMapping
    public ReviewQueueResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer offset, @RequestParam(required = false) Integer limit) {
        int pageOffset = offset == null ? properties.defaultOffset() : offset;
        int pageLimit = limit == null ? properties.defaultLimit() : limit;
        if (pageOffset < 0 || pageOffset > properties.maxOffset()) {
            throw new InvalidRequestException(String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (pageLimit < 1 || pageLimit > properties.maxLimit()) {
            throw new InvalidRequestException(String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }
        List<ProjectReviewCandidate> window = reviews.list(profileId(jwt), pageOffset, pageLimit + 1);
        return new ReviewQueueResponse(window.stream().limit(pageLimit).toList(),
                new PageMetadata(pageOffset, pageLimit, window.size() > pageLimit, properties.noteMaxLength()));
    }

    @PostMapping("/{projectId}/decision")
    public ProjectReviewResult decide(@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProjectReviewRequest request) {
        return reviews.decide(projectId, profileId(jwt), request.decision(), request.verificationStatus(), request.note());
    }

    private static UUID profileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new InvalidAuthenticatedSubjectException();
        try {
            UUID id = UUID.fromString(jwt.getSubject());
            if (!id.toString().equalsIgnoreCase(jwt.getSubject())) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }

    public record ReviewQueueResponse(List<ProjectReviewCandidate> data, PageMetadata meta) { }
    public record PageMetadata(int offset, int limit, boolean hasMore, int noteMaxLength) { }
}
