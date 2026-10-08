package com.pulso.backend.ops.api;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local-only endpoint for demonstrating the application's error logging flow. */
@Profile("log-demo")
@RestController
@RequestMapping("/api/dev/log-demo")
public class LogDemoController {

    @PostMapping("/data-store-failure")
    public void simulateDataStoreFailure() {
        throw new DataAccessResourceFailureException(
                "password=demo-only-value host=demo-database");
    }
}
