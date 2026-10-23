package com.pulso.backend.project.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.application.SearchProjects;
import com.pulso.backend.project.domain.ProjectSearchFilters;

@RestController
@RequestMapping("/api/v1/projects/search")
public class ProjectSearchController {

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
        
        if (offset < 0 || limit < 1 || limit > 50) {
            throw new InvalidRequestException("Invalid offset or limit.");
        }

        ProjectSearchFilters filters = new ProjectSearchFilters(q, sector, stage, location, tags, hasOpenOpportunities);
        SearchProjects.SearchResult result = searchProjects.execute(filters, offset, limit);
        
        return new SearchResponse(result.data(), new SearchMeta(offset, limit, result.totalCount()));
    }

    public record SearchResponse(List<ProjectSummary> data, SearchMeta meta) {}
    public record SearchMeta(int offset, int limit, long totalCount) {}
}