package com.oryxos.boot;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;
import com.oryxos.core.ToolResult;
import com.oryxos.core.agent.ContextLoader;
import com.oryxos.memory.MemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quickstart scenario 4 mechanical part as an automated gate: save_memory
 * writes MEMORY.md, recall_memory returns the matched entry, and the memory
 * view that PromptBuilder injects into the system prompt carries the fact —
 * so a NEW session (or after restart) sees it without re-explanation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"oryxos.root=target/ws-memory"})
class MemoryInjectionGateTest {

    private static final String AGENT = "memobot";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FACT = "用户的项目部署在杭州机房";

    @Autowired
    ToolRegistry toolRegistry;
    @Autowired
    ContextLoader contextLoader;
    @Autowired
    MemoryService memoryService;

    @BeforeAll
    static void createWorkspace() throws Exception {
        Path root = Path.of("target/ws-memory");
        Files.createDirectories(root.resolve("agents").resolve(AGENT));
        Path agentMd = root.resolve("agents").resolve(AGENT).resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            Files.writeString(agentMd, String.join("\n",
                    "---",
                    "name: " + AGENT,
                    "description: memory gate agent",
                    "provider:",
                    "  name: deepseek",
                    "  model: deepseek-chat",
                    "tools: [save_memory, recall_memory]",
                    "bootstrap: []",
                    "---",
                    "",
                    "测试用 Agent。"));
        }
    }

    @Test
    void savedMemoryIsRecallableAndInjectedIntoPromptView() {
        OryxTool save = toolRegistry.get("save_memory");
        OryxTool recall = toolRegistry.get("recall_memory");
        assertNotNull(save);
        assertNotNull(recall);

        ToolResult saved = save.execute(MAPPER.valueToTree(Map.of("content", FACT)));
        assertTrue(saved.isSuccess(), () -> "save_memory 失败: " + saved.getErrorMessage());

        assertTrue(contextLoader.memoryView().contains(FACT), "注入视图必须携带已保存事实");

        ToolResult recalled = recall.execute(MAPPER.valueToTree(Map.of("query", "杭州")));
        assertTrue(recalled.isSuccess());
        assertTrue(recalled.getContent().contains(FACT));
        assertTrue(recalled.getContent().startsWith("- ["));

        assertTrue(memoryService.getMemoryContext(null).contains(FACT));
    }
}
