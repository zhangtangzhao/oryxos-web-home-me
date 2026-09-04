package com.oryxos.boot;

import com.oryxos.core.AgentService;
import com.oryxos.core.llm.LlmClient;
import com.oryxos.core.llm.LlmRequest;
import com.oryxos.core.llm.LlmResult;
import com.oryxos.core.llm.ToolCallSpec;
import com.oryxos.core.session.SessionService;
import com.oryxos.core.Session;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Quickstart scenario 3 as an automated gate (SC-006): a whitelist-outside
 * tool request is intercepted by the sandbox, never executed, and audited as
 * success=false — through the real PromptBuilder/ReActLoop/ToolExecutor chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"oryxos.root=target/ws3"})
class SandboxInterceptionGateTest {

    private static final String AGENT = "sandexbot";

    @Autowired
    AgentService agentService;
    @Autowired
    SessionService sessionService;
    @Autowired
    ToolInvocationRepository toolRepo;

    @BeforeAll
    static void createWorkspace() throws Exception {
        Path root = Path.of("target/ws3");
        Files.createDirectories(root.resolve("agents").resolve(AGENT));
        Path agentMd = root.resolve("agents").resolve(AGENT).resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            Files.writeString(agentMd, String.join("\n",
                    "---",
                    "name: " + AGENT,
                    "description: sandbox gate agent",
                    "identity:",
                    "  agent_name: SandExBot",
                    "  prompt: test",
                    "provider:",
                    "  name: deepseek",
                    "  model: deepseek-chat",
                    "tools: [read_file]",
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
        LlmClient scriptedLlmClient() {
            return new ScriptedLlmClient();
        }
    }

    /** First call demands a whitelist-outside read; second call ends the task. */
    static class ScriptedLlmClient implements LlmClient {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public LlmResult complete(LlmRequest request) {
            LlmResult result = new LlmResult();
            result.setDurationMs(1);
            if (calls.getAndIncrement() == 0) {
                result.setToolCalls(List.of(new ToolCallSpec("call-1", "read_file",
                        Map.of("path", "/etc/passwd"))));
            } else {
                result.setContent("TASK_DONE");
            }
            return result;
        }
    }

    @Test
    void whitelistViolationIsInterceptedAndAudited() {
        Session session = sessionService.getOrCreate("cli", "sand-" + System.nanoTime(), AGENT);

        String reply = agentService.process(session, "读取 /etc/passwd 的内容");

        assertEquals("TASK_DONE", reply);
        var rows = toolRepo.findBySessionId(session.getSessionId());
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).isSuccess(), "白名单外读取必须被拦截");
        assertFalse(rows.get(0).getErrorMessage() == null || rows.get(0).getErrorMessage().isBlank(),
                "拦截记录必须带原因");
        assertEquals("read_file", rows.get(0).getToolName());
    }
}
