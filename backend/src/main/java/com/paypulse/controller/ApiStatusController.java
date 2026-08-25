package com.paypulse.controller;

import com.paypulse.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ApiStatusController {

    private final AppProperties properties;

    public ApiStatusController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/")
    public Map<String, Object> root() {         //General application status
        return Map.of("msg", "PayPulse backend is running", "api", "/api");
        //Map.of creates an immutable map.
    }

    @GetMapping("/api")                         //API status + maintenance flag
    public Map<String, Object> api() {
        return Map.of("msg", "working properly", "maintenance", properties.isMaintenanceMode());
    }
}
