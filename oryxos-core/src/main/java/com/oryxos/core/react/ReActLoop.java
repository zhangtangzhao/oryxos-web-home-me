package com.oryxos.core.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.Message;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.ToolResult;
import com.oryxos.core.audit.AuditLog;
import com.oryxos.core.llm.LlmClient;
import com.oryxos.core.llm.LlmRequest;
import com.oryxos.core.llm.LlmResult;
import com.oryxos.core.llm.ToolCallSpec;
import com.oryxos.core.prompt.PromptBuilder;
import com.oryxos.core.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Self-implemented ReAct loop (constitution II): Reason -> Act -> Observe
 * until the model returns plain text or max_iterations is reached (FR-009/010).
 * Every LLM call and tool call is audited before its result is used (FR-025/026).
 */
@Service
public class ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ToolExecutor toolExecutor;
    private final AuditLog auditLog;

    public ReActLoop(LlmClient llmClient, PromptBuilder promptBuilder,
                     ToolExecutor toolExecutor, AuditLog auditLog) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.toolExecutor = toolExecutor;
        this.auditLog = auditLog;
    }

    public String run(Session session, Profile profile, List<OryxTool> tools) {
        int maxIterations = profile.getSettings() != null
                ? profile.getSettings().getMaxIterations() : 10;
        Profile.ProviderRef providerRef = profile.getProvider();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            LlmRequest request = new LlmRequest();
            request.setProviderName(providerRef.getName());
            request.setModel(providerRef.getModel());
            request.setTemperature(providerRef.getTemperature());
            request.setMessages(promptBuilder.build(session, profile, tools));
            request.setTools(promptBuilder.toolSpecs(tools));

            LlmResult result = llmClient.complete(request);
            auditLog.recordLlmCall(session.getSessionId(), providerRef.getName(), providerRef.getModel(),
                    result.getPromptTokens(), result.getCompletionTokens(), result.getTotalTokens(),
                    result.getDurationMs());

            if (!result.hasToolCalls()) {
                String content = result.getContent() == null ? "" : result.getContent();
                session.addMessage(Message.assistant(content));
                return content;
            }

            session.addMessage(assistantWithToolCalls(result));
            for (ToolCallSpec call : result.getToolCalls()) {
                ToolResult toolResult = toolExecutor.execute(session, call.getName(), call.getArguments());
                String observation = toolResult.isSuccess()
                        ? toolResult.getContent()
                        : "ERROR: " + toolResult.getErrorMessage();
                session.addMessage(Message.tool(call.getName(), call.getId(), observation));
            }
        }

        String forced = "已达到最大迭代次数 " + maxIterations + "（max_iterations），任务被强制终止。"
                + "如需继续，请将任务拆分为更小的步骤。";
        log.warn("ReAct 达到迭代上限: session={} max_iterations={}", session.getSessionId(), maxIterations);
        session.addMessage(Message.assistant(forced));
        return forced;
    }

    private Message assistantWithToolCalls(LlmResult result) {
        Message message = Message.assistant(result.getContent() == null ? "" : result.getContent());
        try {
            message.setToolCallsJson(MAPPER.writeValueAsString(result.getToolCalls()));
        } catch (Exception e) {
            log.warn("tool_calls 序列化失败", e);
        }
        return message;
    }
}
