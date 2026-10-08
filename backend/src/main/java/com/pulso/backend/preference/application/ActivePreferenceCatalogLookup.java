package com.pulso.backend.preference.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ActivePreferenceCatalogLookup {

    Set<UUID> findActiveTagIds(List<UUID> ids);

    Set<UUID> findActiveSkillIds(List<UUID> ids);
}
