package com.oryxos.boot;

import com.oryxos.core.llm.LlmClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T047 (SC-009, 需求文档 §13): all 10 REST endpoints against the contract in
 * contracts/rest-api.md — envelope shape, status semantics (400/404/409),
 * archived-session rejection — with the LLM stubbed (no tool calls).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"oryxos.root=target/ws-rest"})
@AutoConfigureMockMvc
class RestContractTest {

    private static final String AGENT = "hello";

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void createFreshWorkspace() throws Exception {
        Path root = Path.of("target/ws-rest");
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(root.resolve("agents").resolve(AGENT));
        Files.writeString(root.resolve("agents").resolve(AGENT).resolve("AGENT.md"), String.join("\n",
                "---",
                "name: " + AGENT,
                "description: contract test agent",
                "provider:",
                "  name: deepseek",
                "  model: deepseek-chat",
                "tools: [read_file, save_memory, recall_memory]",
                "bootstrap: []",
                "---",
                "",
                "测试用 Agent。"));
    }

    @TestConfiguration
    static class Mocks {
        @Bean
        @Primary
        LlmClient scriptedLlmClient() {
            return request -> {
                var result = new com.oryxos.core.llm.LlmResult();
                result.setContent("REST_OK");
                result.setDurationMs(1);
                return result;
            };
        }
    }

    @Test
    void contractWalkthrough() throws Exception {
        // 9. health
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.agents_loaded").value(1))
                .andExpect(jsonPath("$.data.db_ok").value(true));

        // 10. info
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("oryxos"))
                .andExpect(jsonPath("$.data.uptime_seconds").isNumber());

        // 6. profiles
        // 注：过滤路径返回 JSONArray；期望值用 hasItem 走 Matcher 重载。
        // .value(List.of(...)) 会触发 Spring 6.2 按期望值运行时类（ImmutableCollections$ListN）
        // 重读转换，Jayway 映射不出该类型而静默返回 null。
        mockMvc.perform(get("/api/v1/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profiles[?(@.name=='hello')].provider").value(hasItem("deepseek")));

        // 8. tools
        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tools[?(@.name=='read_file')].type").value(hasItem("builtin")))
                .andExpect(jsonPath("$.data.tools[?(@.name=='save_memory')].type").value(hasItem("memory")));

        // 7. memory（空库也必须是成功信封）
        mockMvc.perform(get("/api/v1/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.items").isArray());
        mockMvc.perform(get("/api/v1/memory").param("query", "任意词"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 1. create session — 400 on unknown profile
        mockMvc.perform(post("/api/v1/sessions").contentType(APPLICATION_JSON)
                        .content("{\"profile\":\"hello\",\"user_id\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session_id").value("http-u1-" + AGENT))
                .andExpect(jsonPath("$.data.status").value("active"));
        mockMvc.perform(post("/api/v1/sessions").contentType(APPLICATION_JSON)
                        .content("{\"profile\":\"nope\",\"user_id\":\"u1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(400));

        // 2. send message
        mockMvc.perform(post("/api/v1/sessions/http-u1-" + AGENT + "/messages").contentType(APPLICATION_JSON)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("REST_OK"))
                .andExpect(jsonPath("$.data.tool_calls").isArray())
                .andExpect(jsonPath("$.data.iterations").isNumber());

        // 2b. 404 on missing session
        mockMvc.perform(post("/api/v1/sessions/ghost/messages").contentType(APPLICATION_JSON)
                        .content("{\"content\":\"?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(404));

        // 3. history now contains both turns
        mockMvc.perform(get("/api/v1/sessions/http-u1-" + AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"));

        // 4. archive is idempotent
        mockMvc.perform(delete("/api/v1/sessions/http-u1-" + AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("archived"));
        mockMvc.perform(delete("/api/v1/sessions/http-u1-" + AGENT))
                .andExpect(status().isOk());

        // FR-029: message to archived session → 409
        mockMvc.perform(post("/api/v1/sessions/http-u1-" + AGENT + "/messages").contentType(APPLICATION_JSON)
                        .content("{\"content\":\"在吗\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(409));

        // 5. stateless invoke — same chain, 404 on unknown agent
        mockMvc.perform(post("/api/v1/agents/" + AGENT + "/invoke").contentType(APPLICATION_JSON)
                        .content("{\"message\":\"1+1=?\",\"user_id\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("REST_OK"))
                .andExpect(jsonPath("$.data.iterations").isNumber());
        mockMvc.perform(post("/api/v1/agents/nope/invoke").contentType(APPLICATION_JSON)
                        .content("{\"message\":\"?\",\"user_id\":\"u1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(404));
    }
}
