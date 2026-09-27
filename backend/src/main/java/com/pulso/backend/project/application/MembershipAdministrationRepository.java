package com.pulso.backend.project.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ProjectMember;

public interface MembershipAdministrationRepository {

    Optional<MembershipActor> findActor(UUID projectId, UUID profileId);

    Optional<ProjectMember> findMember(UUID projectId, UUID memberId);

    List<ProjectMember> findMembers(UUID projectId);

    int updateMembership(
            UUID projectId,
            UUID memberId,
            String expectedRole,
            String expectedMembershipStatus,
            String nextRole,
            String nextMembershipStatus);
}
