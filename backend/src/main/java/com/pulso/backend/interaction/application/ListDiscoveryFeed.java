package com.pulso.backend.interaction.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.ProjectPage;

@Service
public class ListDiscoveryFeed {

    private final ProjectInteractionRepository repository;

    public ListDiscoveryFeed(ProjectInteractionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProjectPage<DiscoveryFeedItem> execute(UUID profileId, int offset, int limit) {
        List<DiscoveryFeedItem> rows = repository.findDiscoveryFeed(profileId, offset, limit + 1);
        boolean hasMore = rows.size() > limit;
        return new ProjectPage<>(
                rows.stream().limit(limit).toList(),
                offset,
                limit,
                hasMore,
                repository.countDiscoveryFeed(profileId));
    }
}
