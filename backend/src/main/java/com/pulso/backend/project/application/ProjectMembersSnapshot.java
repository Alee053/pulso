package com.pulso.backend.project.application;

import java.util.List;

import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ProjectMemberSummary;

public record ProjectMembersSnapshot(MembershipActor actor, List<ProjectMemberSummary> members) {

    public ProjectMembersSnapshot {
        members = List.copyOf(members);
    }
}
