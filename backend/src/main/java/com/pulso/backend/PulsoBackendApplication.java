
package com.pulso.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.pulso.backend.config")
public class PulsoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulsoBackendApplication.class, args);
    }
}
