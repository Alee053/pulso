package com.pulso.backend.interaction.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.DiscoveryProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.interaction.application.GetProjectFeedback;
import com.pulso.backend.interaction.application.ListDiscoveryFeed;
import com.pulso.backend.interaction.application.ListProjectCollection;
import com.pulso.backend.interaction.application.SetProjectFeedback;
import com.pulso.backend.interaction.application.SetSavedProject;
import com.pulso.backend.interaction.domain.DiscoveryFeedCursor;
import com.pulso.backend.interaction.domain.ProjectPage;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/me")
public class ProjectInteractionController {

    private final ListDiscoveryFeed listDiscoveryFeed;
    private final ListProjectCollection listProjectCollection;
    private final SetSavedProject setSavedProject;
    private final SetProjectFeedback setProjectFeedback;
    private final GetProjectFeedback getProjectFeedback;
    private final DiscoveryProperties properties;

    public ProjectInteractionController(
            ListDiscoveryFeed listDiscoveryFeed,
            ListProjectCollection listProjectCollection,
            SetSavedProject setSavedProject,
            SetProjectFeedback setProjectFeedback,
            GetProjectFeedback getProjectFeedback,
            DiscoveryProperties properties) {
        this.listDiscoveryFeed = listDiscoveryFeed;
        this.listProjectCollection = listProjectCollection;
        this.setSavedProject = setSavedProject;
        this.setProjectFeedback = setProjectFeedback;
        this.getProjectFeedback = getProjectFeedback;
        this.properties = properties;
    }

    @GetMapping("/discovery-feed")
    public DiscoveryFeedResponse discoveryFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "${pulso.discovery.default-limit}") int limit) {
        int requestedOffset = offset == null ? 0 : offset;
        validateWindow(requestedOffset, limit);
        if (cursor != null && offset != null) {
            throw new InvalidRequestException(ApiMessages.INVALID_DISCOVERY_CURSOR);
        }
        DiscoveryFeedCursor decodedCursor = null;
        if (cursor != null) {
            try {
                decodedCursor = DiscoveryFeedCursor.decode(cursor);
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException(ApiMessages.INVALID_DISCOVERY_CURSOR);
            }
        }
        return DiscoveryFeedResponse.from(
                listDiscoveryFeed.execute(authenticatedProfileId(jwt), requestedOffset, decodedCursor, limit));
    }

    @GetMapping("/saved-projects")
    public ProjectCollectionResponse savedProjects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "${pulso.discovery.default-offset}") int offset,
            @RequestParam(defaultValue = "${pulso.discovery.default-limit}") int limit) {
        return listCollection(jwt, offset, limit, ListProjectCollection.CollectionType.SAVED);
    }

    @GetMapping("/interested-projects")
    public ProjectCollectionResponse interestedProjects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "${pulso.discovery.default-offset}") int offset,
            @RequestParam(defaultValue = "${pulso.discovery.default-limit}") int limit) {
        return listCollection(jwt, offset, limit, ListProjectCollection.CollectionType.INTERESTED);
    }

    @PutMapping("/saved-projects/{projectId}")
    public ProjectInteractionMutationResponse saveProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId) {
        setSavedProject.execute(authenticatedProfileId(jwt), projectId, true);
        return ProjectInteractionMutationResponse.saved(projectId, true);
    }

    @DeleteMapping("/saved-projects/{projectId}")
    public ProjectInteractionMutationResponse unsaveProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId) {
        setSavedProject.execute(authenticatedProfileId(jwt), projectId, false);
        return ProjectInteractionMutationResponse.saved(projectId, false);
    }

    @PutMapping("/project-feedback/{projectId}")
    public ProjectInteractionMutationResponse projectFeedback(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @RequestBody(required = false) ProjectFeedbackRequest request) {
        if (request == null) {
            throw new InvalidRequestException(ApiMessages.REQUEST_BODY_REQUIRED);
        }
        var type = request.toFeedbackType();
        setProjectFeedback.execute(
                authenticatedProfileId(jwt),
                projectId,
                type,
                request.weightsOrNull());
        return ProjectInteractionMutationResponse.feedback(projectId, type.name());
    }

    @GetMapping("/project-feedback/{projectId}")
    public ProjectInteractionMutationResponse currentProjectFeedback(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId) {
        var type = getProjectFeedback.execute(authenticatedProfileId(jwt), projectId);
        return ProjectInteractionMutationResponse.feedback(
                projectId, type == null ? null : type.name());
    }

    @DeleteMapping("/project-feedback/{projectId}")
    public ProjectInteractionMutationResponse clearProjectFeedback(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId) {
        setProjectFeedback.clear(authenticatedProfileId(jwt), projectId);
        return ProjectInteractionMutationResponse.feedback(projectId, null);
    }

    private ProjectCollectionResponse listCollection(
            Jwt jwt,
            int offset,
            int limit,
            ListProjectCollection.CollectionType type) {
        validateWindow(offset, limit);
        ProjectPage<ProjectSummary> page = listProjectCollection.execute(
                authenticatedProfileId(jwt),
                type,
                offset,
                limit);
        return ProjectCollectionResponse.from(page);
    }

    private void validateWindow(int offset, int limit) {
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidAuthenticatedSubjectException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }
}
