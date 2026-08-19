package com.pulso.backend.discovery.api;

import java.util.List;

public record StartupListResponse(
        List<StartupResponse> data,
        PageMetadata meta) {

    public record PageMetadata(int offset, int limit, boolean hasMore) {
    }
}
