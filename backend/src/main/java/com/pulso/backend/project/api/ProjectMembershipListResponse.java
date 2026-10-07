package com.pulso.backend.project.api;

import java.util.List;

public record ProjectMembershipListResponse(List<ManagedProjectMemberResponse> data, PageMetadata meta) {
    public record PageMetadata(int offset, int limit, boolean hasMore) {
    }
}
