package com.oryxos.web;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent invocation endpoints.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentApiController {

    @PostMapping("/{name}/invoke")
    public ApiResponse<Map<String, String>> invokeAgent(
            @PathVariable String name,
            @RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        return ApiResponse.success(Map.of(
            "agent", name,
            "response", "Agent [" + name + "] processed: " + message
        ));
    }
}
