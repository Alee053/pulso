package com.pulso.backend.project.api;

import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.preference.domain.OnboardingOptions;
import com.pulso.backend.project.application.SearchProjects;
import com.pulso.backend.project.domain.ProjectSearchFilters;

@RestController
@RequestMapping("/api/v1/projects/search")
public class ProjectSearchController {

    static final int MAX_LIMIT = 50;
    static final int MAX_TEXT_LENGTH = 100;
    static final int MAX_TAGS = 10;

    private final SearchProjects searchProjects;

    public ProjectSearchController(SearchProjects searchProjects) {
        this.searchProjects = searchProjects;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Boolean hasOpenOpportunities,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        if (offset < 0 || limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("Invalid offset or limit.");
        }

        ProjectSearchFilters filters = new ProjectSearchFilters(
                text(q, "q"),
                text(sector, "sector"),
                stage(stage),
                text(location, "location"),
                tags(tags),
                hasOpenOpportunities);
        SearchProjects.SearchResult result = searchProjects.execute(filters, offset, limit);

        return new SearchResponse(result.data(), new SearchMeta(offset, limit, result.totalCount()));
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new InvalidRequestException("Invalid search filter: " + name + ".");
        }
        return trimmed;
    }

    private static String stage(String value) {
        String stage = text(value, "stage");
        if (stage == null) {
            return null;
        }
        String normalized = stage.toUpperCase(Locale.ROOT);
        if (!OnboardingOptions.STAGES.contains(normalized)) {
            throw new InvalidRequestException("Invalid search filter: stage.");
        }
        return normalized;
    }

    private static List<String> tags(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> tags = values.stream()
                .map(tag -> text(tag, "tags"))
                .filter(tag -> tag != null)
                .distinct()
                .toList();
        if (tags.size() > MAX_TAGS) {
            throw new InvalidRequestException("Invalid search filter: tags.");
        }
        return tags;
    }

    public record SearchResponse(List<ProjectSummary> data, SearchMeta meta) {}

    public record SearchMeta(int offset, int limit, long totalCount) {}
}
