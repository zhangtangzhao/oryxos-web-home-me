package com.oryxos.web;

import com.oryxos.core.Profile;
import com.oryxos.core.agent.AgentLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profile (Agent) information (contract §6). */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileApiController {

    private final AgentLoader agentLoader;

    public ProfileApiController(AgentLoader agentLoader) {
        this.agentLoader = agentLoader;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listProfiles() {
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (Profile p : agentLoader.list()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", p.getName());
            entry.put("description", p.getDescription() == null ? "" : p.getDescription());
            entry.put("provider", p.getProvider() == null ? "" : p.getProvider().getName());
            entry.put("model", p.getProvider() == null ? "" : p.getProvider().getModel());
            entry.put("tools", p.getTools() == null ? List.of() : p.getTools());
            profiles.add(entry);
        }
        return ApiResponse.ok(Map.of("profiles", profiles));
    }
}
