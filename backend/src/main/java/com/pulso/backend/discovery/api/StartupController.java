package com.pulso.backend.discovery.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.DiscoveryProperties;
import com.pulso.backend.discovery.application.ListPublishedStartups;
import com.pulso.backend.discovery.domain.StartupSummary;

@RestController
@RequestMapping("/api/v1/startups")
public class StartupController {

    private final ListPublishedStartups listPublishedStartups;
    private final DiscoveryProperties properties;

    public StartupController(
            ListPublishedStartups listPublishedStartups,
            DiscoveryProperties properties) {
        this.listPublishedStartups = listPublishedStartups;
        this.properties = properties;
    }

    @GetMapping
    public StartupListResponse list(
            @RequestParam(defaultValue = "${pulso.discovery.default-offset}") int offset,
            @RequestParam(defaultValue = "${pulso.discovery.default-limit}") int limit) {
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

    private void validateOffset(int offset) {
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }
    }
}
