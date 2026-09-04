package com.oryxos.web;

import com.oryxos.core.AgentService;
import com.oryxos.core.Message;
import com.oryxos.core.Session;
import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.audit.AuditLog;
import com.oryxos.core.audit.ToolCallRecord;
import com.oryxos.core.session.SessionArchivedException;
import com.oryxos.core.session.SessionService;
import com.oryxos.core.session.SessionStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.oryxos.web.GlobalExceptionHandler.ResourceNotFoundException;

/**
 * Session management endpoints (FR-019, FR-029): create / send message /
 * history / archive. Message to an archived session returns 409.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {

    private final AgentLoader agentLoader;
    private final SessionService sessionService;
    private final SessionStore sessionStore;
    private final AgentService agentService;
    private final AuditLog auditLog;

    public SessionApiController(AgentLoader agentLoader, SessionService sessionService,
                                SessionStore sessionStore, AgentService agentService, AuditLog auditLog) {
        this.agentLoader = agentLoader;
        this.sessionService = sessionService;
        this.sessionStore = sessionStore;
        this.agentService = agentService;
        this.auditLog = auditLog;
    }

    /** create; unknown/invalid profile is a 400 (contract §1). */
    @PostMapping
    public ApiResponse<Map<String, Object>> createSession(@RequestBody Map<String, String> request) {
        String profile = required(request, "profile");
        String userId = required(request, "user_id");
        agentLoader.require(profile); // IllegalArgumentException -> 400

        Session session = sessionService.getOrCreate("http", userId, profile);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", session.getSessionId());
        data.put("profile_name", session.getProfileName());
        data.put("status", session.getStatus().name().toLowerCase());
        data.put("created_at", session.getCreatedAt());
        return ApiResponse.ok(data);
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String content = required(request, "content");
        Session session = resolvableActive(id);

        long llmBefore = auditLog.countLlmCalls(id);
        int toolsBefore = auditLog.toolInvocations(id).size();
        String reply = agentService.process(session, content);

        List<ToolCallRecord> allInvocations = auditLog.toolInvocations(id);
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        for (int i = 0; i < allInvocations.size() - toolsBefore; i++) {
            ToolCallRecord r = allInvocations.get(i);
            toolCalls.add(Map.of("tool", r.toolName(), "success", r.success()));
        }
        long iterations = auditLog.countLlmCalls(id) - llmBefore;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", id);
        data.put("reply", reply);
        data.put("tool_calls", toolCalls);
        data.put("iterations", iterations);
        return ApiResponse.ok(data);
    }

    /** History is readable for archived sessions too (archive keeps data). */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getSession(@PathVariable String id) {
        Session session = sessionStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + id));
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message m : session.getMessages()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", m.getRole().name().toLowerCase());
            entry.put("content", m.getContent());
            if (m.getTimestamp() != null) {
                entry.put("created_at", m.getTimestamp());
            }
            messages.add(entry);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", session.getSessionId());
        data.put("profile_name", session.getProfileName());
        data.put("status", session.getStatus().name().toLowerCase());
        data.put("messages", messages);
        return ApiResponse.ok(data);
    }

    /** Archive is idempotent: re-archiving still returns 200 (contract §4). */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> archiveSession(@PathVariable String id) {
        Session session = sessionStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + id));
        if (session.getStatus() != Session.SessionStatus.ARCHIVED) {
            sessionService.archive(session);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", id);
        data.put("status", "archived");
        data.put("archived_at", session.getArchivedAt() == null ? Instant.now() : session.getArchivedAt());
        return ApiResponse.ok(data);
    }

    /** 404 when absent, 409 (via SessionArchivedException) when archived. */
    private Session resolvableActive(String id) {
        return sessionStore.findById(id)
                .map(s -> {
                    if (s.getStatus() == Session.SessionStatus.ARCHIVED) {
                        throw new SessionArchivedException(id);
                    }
                    return s;
                })
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + id));
    }

    private static String required(Map<String, String> request, String field) {
        String value = request == null ? null : request.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数: " + field);
        }
        return value.trim();
    }
}
