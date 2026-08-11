package com.oryxos.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * System-level endpoints: health check and runtime info.
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.success(Map.of(
            "name", "OryxOS",
            "version", "1.0.0-SNAPSHOT",
            "javaVersion", System.getProperty("java.version"),
            "osName", System.getProperty("os.name"),
            "osArch", System.getProperty("os.arch")
        ));
    }
}
