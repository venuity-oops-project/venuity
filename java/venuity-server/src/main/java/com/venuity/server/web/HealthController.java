package com.venuity.server.web;

import com.venuity.server.api.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final String version;

    public HealthController(@Value("${info.app.version:1.0.0-SNAPSHOT}") String version) {
        this.version = version;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP", version);
    }
}
