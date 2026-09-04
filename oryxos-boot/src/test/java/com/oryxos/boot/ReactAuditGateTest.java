package com.oryxos.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.AgentService;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Session;
import com.oryxos.core.ToolRegistry;
import com.oryxos.core.ToolResult;
import com.oryxos.core.llm.LlmClient;
import com.oryxos.core.llm.LlmRequest;
import com.oryxos.core.llm.LlmResult;
import com.oryxos.core.llm.ToolCallSpec;
import com.oryxos.core.session.SessionService;
import com.oryxos.storage.LlmCallRepository;
import com.oryxos.storage.ToolInvocationRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-regression gate (R-9 / FR-009 / constitution II): under a scripted mock
 * LLM, a tool task must produce EXACTLY ONE tool_invocations row — Spring AI
 * must never execute tools on its own — plus one llm_calls row per ReAct
 * iteration (SC-003 audit trail).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"oryxos.root=target/gate-ws"})
class ReactAuditGateTest {

    private static final String AGENT = "gatebot";

    @Autowired
    AgentService agentService;
    @Autowired
    SessionService sessionService;
    @Autowired
    ToolInvocationRepository toolRepo;
    @Autowired
    LlmCallRepository llmRepo;

    @BeforeAll
    static void createWorkspace() throws Exception {
        Path root = Path.of("target/gate-ws");
        Files.createDirectories(root.resolve("agents").resolve(AGENT));
        Path agentMd = root.resolve("agents").resolve(AGENT).resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            Files.writeString(agentMd, String.join("\n",
                    "---",
                    "name: " + AGENT,
                    "description: gate test agent",
                    "identity:",
                    "  agent_name: GateBot",
                    "  prompt: test",
                    "provider:",
                    "  name: deepseek",
                    "  model: deepseek-chat",
                    "tools: [echo_test]",
                    "bootstrap: []",
                    "settings:",
                    "  max_iterations: 5",
                    "---",
                    "",
                    "测试用 Agent。"));
        }
    }

    @TestConfiguration
    static class Mocks {

        @Bean
        @Primary
        ToolRegistry mockToolRegistry() {
            com.oryxos.tool.InMemoryToolRegistry registry = new com.oryxos.tool.InMemoryToolRegistry();
            registry.register(new EchoTestTool());
            return registry;
        }

        @Bean
        @Primary
        LlmClient mockLlmClient() {
            return new ScriptedLlmClient();
        }
    }

    /** First call requests the tool; second call returns the final answer. */
    static class ScriptedLlmClient implements LlmClient {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public LlmResult complete(LlmRequest request) {
            LlmResult result = new LlmResult();
            result.setDurationMs(1);
            if (calls.getAndIncrement() == 0) {
                ToolCallSpec toolCall = new ToolCallSpec("call-1", "echo_test", Map.of("text", "hello"));
                result.setToolCalls(List.of(toolCall));
            } else {
                result.setContent("TASK_DONE");
            }
            return result;
        }
    }

    static class EchoTestTool implements OryxTool {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String getName() { return "echo_test"; }

        @Override
        public String getDescription() { return "test-only echo tool"; }

        @Override
        public JsonNode getInputSchema() {
            return mapper.createObjectNode();
        }

        @Override
        public ToolResult execute(JsonNode input) {
            return ToolResult.success("echo:" + input.path("text").asText(""));
        }
    }

    @Test
    void toolExecutesExactlyOnceAndAuditsEveryLlmCall() {
        Session session = sessionService.getOrCreate("cli", "gate-" + System.nanoTime(), AGENT);

        String reply = agentService.process(session, "执行一次工具任务");

        assertEquals("TASK_DONE", reply);
        var toolRows = toolRepo.findBySessionId(session.getSessionId());
        assertEquals(1, toolRows.size(), "工具必须恰好执行一次（防 Spring AI 自动执行双重调用）");
        assertTrue(toolRows.get(0).isSuccess());
        assertEquals("echo_test", toolRows.get(0).getToolName());

        var llmRows = llmRepo.findBySessionId(session.getSessionId());
        assertEquals(2, llmRows.size(), "每次 ReAct 迭代必须落一条 llm_calls 审计");
        assertEquals("deepseek", llmRows.get(0).getProvider());
        assertEquals("deepseek-chat", llmRows.get(0).getModel());
    }
}
