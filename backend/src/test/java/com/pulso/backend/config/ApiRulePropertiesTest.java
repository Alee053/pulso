package com.pulso.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class ApiRulePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(ApiRulePropertiesConfiguration.class);

    @Test
    void loadsSafeDefaultsFromApplicationConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DiscoveryProperties.class))
                    .isEqualTo(new DiscoveryProperties(0, 20, 50, 100_000));
            assertThat(context.getBean(CatalogProperties.class))
                    .isEqualTo(new CatalogProperties(500));
            assertThat(context.getBean(PreferenceProperties.class))
                    .isEqualTo(new PreferenceProperties(10, 40, 120));
            assertThat(context.getBean(ProfileProperties.class))
                    .isEqualTo(new ProfileProperties(120, 500, 500, 120, 120));
            assertThat(context.getBean(ProjectProperties.class))
                    .isEqualTo(new ProjectProperties(2, 120, 160, 240, 5_000, 120, 40, 254));
        });
    }

    @Test
    void bindsEnvironmentOverridesForEveryApiRuleGroup() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource(
                                "test-api-rule-environment",
                                Map.ofEntries(
                                        Map.entry("PULSO_DISCOVERY_DEFAULT_OFFSET", "3"),
                                        Map.entry("PULSO_DISCOVERY_DEFAULT_LIMIT", "7"),
                                        Map.entry("PULSO_DISCOVERY_MAX_LIMIT", "9"),
                                        Map.entry("PULSO_DISCOVERY_MAX_OFFSET", "30"),
                                        Map.entry("PULSO_CATALOGS_MAX_ITEMS_PER_CATALOG", "20"),
                                        Map.entry("PULSO_PREFERENCES_MAX_ITEMS_PER_FIELD", "4"),
                                        Map.entry("PULSO_PREFERENCES_MAX_TOKEN_LENGTH", "16"),
                                        Map.entry("PULSO_PREFERENCES_SECTOR_ID_MAX_LENGTH", "36"),
                                        Map.entry("PULSO_PROFILE_DISPLAY_NAME_MAX_LENGTH", "80"),
                                        Map.entry("PULSO_PROFILE_AVATAR_PATH_MAX_LENGTH", "300"),
                                        Map.entry("PULSO_PROFILE_SHORT_BIO_MAX_LENGTH", "240"),
                                        Map.entry("PULSO_PROFILE_CITY_MAX_LENGTH", "80"),
                                        Map.entry("PULSO_PROFILE_DEPARTMENT_MAX_LENGTH", "80"),
                                        Map.entry("PULSO_PROJECT_SLUG_MIN_LENGTH", "3"),
                                        Map.entry("PULSO_PROJECT_SLUG_MAX_LENGTH", "40"),
                                        Map.entry("PULSO_PROJECT_NAME_MAX_LENGTH", "80"),
                                        Map.entry("PULSO_PROJECT_TAGLINE_MAX_LENGTH", "100"),
                                        Map.entry("PULSO_PROJECT_DESCRIPTION_MAX_LENGTH", "1000"),
                                        Map.entry("PULSO_PROJECT_CITY_MAX_LENGTH", "80"),
                                        Map.entry("PULSO_PROJECT_STAGE_TOKEN_MAX_LENGTH", "24"),
                                        Map.entry("PULSO_PROJECT_INVITEE_EMAIL_MAX_LENGTH", "180")))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DiscoveryProperties.class))
                            .isEqualTo(new DiscoveryProperties(3, 7, 9, 30));
                    assertThat(context.getBean(CatalogProperties.class))
                            .isEqualTo(new CatalogProperties(20));
                    assertThat(context.getBean(PreferenceProperties.class))
                            .isEqualTo(new PreferenceProperties(4, 16, 36));
                    assertThat(context.getBean(ProfileProperties.class))
                            .isEqualTo(new ProfileProperties(80, 300, 240, 80, 80));
                    assertThat(context.getBean(ProjectProperties.class))
                            .isEqualTo(new ProjectProperties(3, 40, 80, 100, 1_000, 80, 24, 180));
                });
    }

    @Test
    void rejectsNonPositiveApiRuleValues() {
        assertConfigurationRejected("pulso.catalogs.max-items-per-catalog=0");
        assertConfigurationRejected("pulso.preferences.max-items-per-field=0");
        assertConfigurationRejected("pulso.profile.short-bio-max-length=-1");
        assertConfigurationRejected("pulso.project.description-max-length=0");
    }

    @Test
    void rejectsInconsistentPaginationAndSlugRanges() {
        assertConfigurationRejected("pulso.discovery.default-limit=51");
        assertConfigurationRejected("pulso.discovery.default-offset=100001");
        assertConfigurationRejected("pulso.project.slug-min-length=121");
    }

    private void assertConfigurationRejected(String propertyValue) {
        contextRunner
                .withPropertyValues(propertyValue)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            DiscoveryProperties.class,
            CatalogProperties.class,
            PreferenceProperties.class,
            ProfileProperties.class,
            ProjectProperties.class
    })
    static class ApiRulePropertiesConfiguration {
    }
}
