package com.pulso.backend.interaction.domain;

import java.util.List;

public record DiscoveryFeedPage(
        List<DiscoveryFeedItem> data,
        int offset,
        int limit,
        boolean hasMore,
        long totalCount,
        String nextCursor) {
}
