package com.oryxos.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Memory query endpoints.
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryApiController {

    @GetMapping
    public ApiResponse<List<Map<String, String>>> listMemory() {
        return ApiResponse.success(List.of());
    }
}
