package com.pulso.backend.interaction.domain;

import java.util.List;

public record ProjectPage<T>(
        List<T> data,
        int offset,
        int limit,
        boolean hasMore,
        long totalCount) {
}
