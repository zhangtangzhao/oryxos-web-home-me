package com.oryxos.web;

import com.oryxos.core.AgentService;
import com.oryxos.core.Session;
import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.audit.AuditLog;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.oryxos.web.GlobalExceptionHandler.ResourceNotFoundException;

/**
 * Stateless agent invocation (FR-021): same AgentService chain as every other
 * trigger source, but the session is ephemeral — processed, audited with the
 * temporary session_id, never persisted (contract §5).
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentApiController {

    private final AgentLoader agentLoader;
    private final AgentService agentService;
    private final AuditLog auditLog;

    public AgentApiController(AgentLoader agentLoader, AgentService agentService, AuditLog auditLog) {
        this.agentLoader = agentLoader;
        this.agentService = agentService;
        this.auditLog = auditLog;
    }

    @PostMapping("/{name}/invoke")
    public ApiResponse<Map<String, Object>> invokeAgent(
            @PathVariable String name,
            @RequestBody Map<String, String> request) {
        try {
            agentLoader.require(name);
        } catch (IllegalArgumentException e) {
            // 契约：Agent 不存在 → 404
            throw new ResourceNotFoundException(e.getMessage());
        }
        String message = request == null || request.get("message") == null || request.get("message").isBlank()
                ? null : request.get("message").trim();
        if (message == null) {
            throw new IllegalArgumentException("缺少必填参数: message");
        }
        String userId = request.get("user_id") == null || request.get("user_id").isBlank()
                ? "anonymous" : request.get("user_id").trim();

        String tempSessionId = "http-invoke-" + UUID.randomUUID();
        Session session = new Session(tempSessionId, name, "http", userId);
        session.setEphemeral(true);

        long llmBefore = auditLog.countLlmCalls(tempSessionId);
        String reply = agentService.process(session, message);
        long iterations = auditLog.countLlmCalls(tempSessionId) - llmBefore;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agent", name);
        data.put("reply", reply);
        data.put("iterations", iterations);
        return ApiResponse.ok(data);
    }
}
