package com.pulso.backend.project.api;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.project.domain.ProjectMemberSummary;

public record ProjectMembersResponse(List<MemberResponse> data, ActorResponse actor, PageMetadata meta) {

    public record MemberResponse(
            UUID id,
            UUID projectId,
            UUID profileId,
            String role,
            String membershipStatus,
            String displayName,
            String displayTitle) {

        static MemberResponse from(ProjectMemberSummary member) {
            return new MemberResponse(member.id(), member.projectId(), member.profileId(),
                    member.role(), member.membershipStatus(), member.displayName(), member.displayTitle());
        }
    }

    public record ActorResponse(String role, String membershipStatus) {
    }

    public record PageMetadata(int offset, int limit, boolean hasMore) {
    }
}
