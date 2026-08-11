package com.oryxos.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Session management endpoints.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {

    @PostMapping
    public ApiResponse<Map<String, String>> createSession(@RequestBody Map<String, String> request) {
        String sessionId = UUID.randomUUID().toString();
        return ApiResponse.success("Session created", Map.of("session_id", sessionId));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<Map<String, String>> sendMessage(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        return ApiResponse.success(Map.of(
            "session_id", id,
            "status", "processed",
            "message", "Message received: " + request.getOrDefault("content", "")
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getSession(@PathVariable String id) {
        return ApiResponse.success(Map.of(
            "session_id", id,
            "status", "active"
        ));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> archiveSession(@PathVariable String id) {
        return ApiResponse.success(Map.of(
            "session_id", id,
            "status", "archived"
        ));
    }
}
