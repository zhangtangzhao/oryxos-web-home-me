package com.oryxos.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Tool information endpoints.
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolApiController {

    @GetMapping
    public ApiResponse<List<Map<String, String>>> listTools() {
        return ApiResponse.success(List.of(
            Map.of("name", "read_file", "description", "Read file content"),
            Map.of("name", "write_file", "description", "Write file content"),
            Map.of("name", "list_dir", "description", "List directory contents"),
            Map.of("name", "shell", "description", "Execute shell command"),
            Map.of("name", "http_get", "description", "HTTP GET request"),
            Map.of("name", "http_post", "description", "HTTP POST request"),
            Map.of("name", "save_memory", "description", "Save to long-term memory"),
            Map.of("name", "recall_memory", "description", "Recall from long-term memory"),
            Map.of("name", "notify", "description", "Send notification via webhook")
        ));
    }
}
