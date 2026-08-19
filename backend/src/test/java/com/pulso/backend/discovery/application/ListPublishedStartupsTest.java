package com.pulso.backend.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.discovery.domain.StartupSummary;

class ListPublishedStartupsTest {

    @Test
    void delegatesTheRequestedPageWindowToTheCatalog() {
        StartupSummary startup = new StartupSummary(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "eco-pack",
                "EcoPack Bolivia",
                "Envases biodegradables",
                "Sostenibilidad",
                "Cochabamba",
                "MVP");
        CapturingCatalog catalog = new CapturingCatalog(List.of(startup));

        List<StartupSummary> result = new ListPublishedStartups(catalog).execute(10, 21);

        assertThat(result).containsExactly(startup);
        assertThat(catalog.requestedOffset).isEqualTo(10);
        assertThat(catalog.requestedLimit).isEqualTo(21);
    }

    private static final class CapturingCatalog implements StartupCatalog {

        private final List<StartupSummary> result;
        private int requestedOffset;
        private int requestedLimit;

        private CapturingCatalog(List<StartupSummary> result) {
            this.result = result;
        }

        @Override
        public List<StartupSummary> findPublished(int offset, int limit) {
            requestedOffset = offset;
            requestedLimit = limit;
            return result;
        }
    }
}
