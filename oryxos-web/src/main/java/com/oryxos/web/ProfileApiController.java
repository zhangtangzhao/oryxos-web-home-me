package com.oryxos.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Profile information endpoints.
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileApiController {

    @GetMapping
    public ApiResponse<List<Map<String, String>>> listProfiles() {
        return ApiResponse.success(List.of(
            Map.of("name", "default", "description", "Default agent profile")
        ));
    }
}
