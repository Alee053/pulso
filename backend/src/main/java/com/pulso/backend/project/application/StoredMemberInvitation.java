package com.pulso.backend.project.application;

import java.util.UUID;
import com.pulso.backend.project.domain.MemberInvitation;

public record StoredMemberInvitation(UUID id, MemberInvitation invitation) { }

