package com.pulso.backend.catalog.application;

import java.util.Optional;
import java.util.UUID;

public interface ActiveSectorLookup {

    Optional<UUID> findActiveSectorId(String identifier);
}
