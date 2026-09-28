package com.pulso.backend.project.application;

import java.util.Optional;

import com.pulso.backend.project.domain.PublicProjectDetail;

public interface PublicProjectRepository {

    Optional<PublicProjectDetail> findPublishedByReference(String projectRef);
}
