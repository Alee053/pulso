package com.pulso.backend.discovery.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.discovery.application.ListPublishedStartups;
import com.pulso.backend.discovery.domain.StartupSummary;

@RestController
@RequestMapping("/api/v1/startups")
public class StartupController {

    private static final int MAX_LIMIT = 50;
    private static final int MAX_OFFSET = 100_000;

    private final ListPublishedStartups listPublishedStartups;

    public StartupController(ListPublishedStartups listPublishedStartups) {
        this.listPublishedStartups = listPublishedStartups;
    }

    @GetMapping
    public StartupListResponse list(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        validateOffset(offset);
        validateLimit(limit);

        List<StartupSummary> results = listPublishedStartups.execute(offset, limit + 1);
        boolean hasMore = results.size() > limit;
        List<StartupResponse> data = results.stream()
                .limit(limit)
                .map(StartupResponse::from)
                .toList();

        return new StartupListResponse(
                data,
                new StartupListResponse.PageMetadata(offset, limit, hasMore));
    }

    private static void validateOffset(int offset) {
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new InvalidRequestException("offset must be between 0 and " + MAX_OFFSET + ".");
        }
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT + ".");
        }
    }
}
