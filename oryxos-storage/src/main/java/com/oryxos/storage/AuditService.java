package com.oryxos.storage;

import com.oryxos.core.audit.AuditLog;
import com.oryxos.core.audit.ToolCallRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Audit write facade over tool_invocations / llm_calls (FR-025/026, constitution V).
 * Callers must persist BEFORE returning results to the model/user ("audit first").
 */
@Service
public class AuditService implements AuditLog {

    /** Keep oversized payloads from bloating SQLite rows. */
    private static final int MAX_JSON_LENGTH = 8000;

    private final ToolInvocationRepository toolRepo;
    private final LlmCallRepository llmRepo;

    public AuditService(ToolInvocationRepository toolRepo, LlmCallRepository llmRepo) {
        this.toolRepo = toolRepo;
        this.llmRepo = llmRepo;
    }

    @Override
    public void recordLlmCall(String sessionId, String provider, String model,
                              int promptTokens, int completionTokens, int totalTokens, long durationMs) {
        LlmCallEntity e = new LlmCallEntity();
        e.setSessionId(sessionId);
        e.setProvider(provider);
        e.setModel(model);
        e.setPromptTokens(promptTokens);
        e.setCompletionTokens(completionTokens);
        e.setTotalTokens(totalTokens);
        e.setDurationMs(durationMs);
        e.setCreatedAt(Instant.now());
        llmRepo.save(e);
    }

    @Override
    public void recordToolInvocation(String sessionId, String toolName, String inputJson,
                                     String resultJson, boolean success, String errorMessage, long durationMs) {
        ToolInvocationEntity e = new ToolInvocationEntity();
        e.setSessionId(sessionId);
        e.setToolName(toolName);
        e.setInputJson(truncate(inputJson));
        e.setResultJson(truncate(resultJson));
        e.setSuccess(success);
        e.setErrorMessage(errorMessage);
        e.setDurationMs(durationMs);
        e.setCreatedAt(Instant.now());
        toolRepo.save(e);
    }

    @Override
    public List<ToolCallRecord> toolInvocations(String sessionId) {
        return toolRepo.findBySessionId(sessionId).stream()
                .sorted(Comparator.comparing(ToolInvocationEntity::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(e -> new ToolCallRecord(e.getToolName(), e.isSuccess(), e.getErrorMessage(),
                        e.getDurationMs(), e.getCreatedAt()))
                .toList();
    }

    @Override
    public long countLlmCalls(String sessionId) {
        return llmRepo.countBySessionId(sessionId);
    }

    private static String truncate(String s) {
        if (s == null || s.length() <= MAX_JSON_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_JSON_LENGTH) + "…(truncated)";
    }
}
