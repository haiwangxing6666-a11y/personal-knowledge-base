package com.ithwx.personalknowledgebase.controller;

import com.ithwx.personalknowledgebase.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String applicationName;

    public HealthController(
            @Value("${spring.application.name}")
            String applicationName
    ) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                applicationName
        );
    }
}