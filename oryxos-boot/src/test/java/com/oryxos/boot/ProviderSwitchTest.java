package com.oryxos.boot;

import com.oryxos.core.llm.LlmClient;
import com.oryxos.core.llm.LlmRequest;
import com.oryxos.core.llm.LlmResult;
import com.oryxos.provider.DefaultProviderService;
import com.oryxos.provider.ProviderProperties;
import com.oryxos.storage.LlmCallEntity;
import com.oryxos.storage.LlmCallRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T055 (FR-006/007, SC-004, quickstart 场景 7): Profile 只引用 provider name，
 * 运行时经显式 Map 映射路由并落审计。注：任务原文写在 oryxos-provider，但
 * AgentService + 审计装配在 boot 上下文，故放此处验证端到端切换链路。
 *
 * <p>三段验证：
 * ① 两个 Agent 分别指向 deepseek / kimi，invoke 后 llm_calls 各自记录对应
 *    provider/model（切换 = 改 AGENT.md 的 provider.name，映射随之生效）；
 * ② 显式 Map 注册 kimi + qwen（T051/T052，配置驱动，无类型扫描、无网络调用）；
 * ③ 未注册/非法配置的报错点名 env 变量与全部字段（FR-008）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"oryxos.root=target/ws-switch"})
@AutoConfigureMockMvc
class ProviderSwitchTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LlmCallRepository llmCallRepository;

    @BeforeAll
    static void createFreshWorkspace() throws Exception {
        Path root = Path.of("target/ws-switch");
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        writeAgent(root, "hello", "deepseek", "deepseek-chat");
        writeAgent(root, "bot2", "kimi", "moonshot-v1-8k");
    }

    private static void writeAgent(Path root, String name, String provider, String model) throws Exception {
        Files.createDirectories(root.resolve("agents").resolve(name));
        Files.writeString(root.resolve("agents").resolve(name).resolve("AGENT.md"), String.join("\n",
                "---",
                "name: " + name,
                "description: provider switch test agent",
                "provider:",
                "  name: " + provider,
                "  model: " + model,
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
                var result = new LlmResult();
                result.setContent("SWITCH_OK");
                result.setDurationMs(1);
                return result;
            };
        }
    }

    @Test
    void profileSwitchDrivesMappingAndAudit() throws Exception {
        // invoke 是临时会话（http-invoke-<uuid>），审计不可按稳定 id 查询；
        // 场景 7 的验证形态 = 持久会话发消息后查 llm_calls。
        mockMvc.perform(post("/api/v1/sessions").contentType(APPLICATION_JSON)
                        .content("{\"profile\":\"hello\",\"user_id\":\"u1\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sessions").contentType(APPLICATION_JSON)
                        .content("{\"profile\":\"bot2\",\"user_id\":\"u1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sessions/http-u1-hello/messages").contentType(APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("SWITCH_OK"));
        mockMvc.perform(post("/api/v1/sessions/http-u1-bot2/messages").contentType(APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("SWITCH_OK"));

        List<LlmCallEntity> helloRows = llmCallRepository.findBySessionId("http-u1-hello");
        assertTrue(helloRows.size() >= 1, "hello 会话应有 llm_calls 审计");
        assertTrue(helloRows.stream().allMatch(r ->
                        "deepseek".equals(r.getProvider()) && "deepseek-chat".equals(r.getModel())),
                "hello 的审计应记录 deepseek/deepseek-chat");

        List<LlmCallEntity> bot2Rows = llmCallRepository.findBySessionId("http-u1-bot2");
        assertTrue(bot2Rows.size() >= 1, "bot2 会话应有 llm_calls 审计");
        assertTrue(bot2Rows.stream().allMatch(r ->
                        "kimi".equals(r.getProvider()) && "moonshot-v1-8k".equals(r.getModel())),
                "bot2 的审计应记录 kimi/moonshot-v1-8k");
    }

    /** T051/T052: 配置驱动的显式 Map 注册 —— PATH 环境变量恒存在充当密钥占位，构造期无网络调用。 */
    @Test
    void explicitMappingRegistersKimiAndQwenWithoutTypeScanning() {
        DefaultProviderService service = new DefaultProviderService(twoProviderProps());
        assertTrue(service.getProviderNames().containsAll(List.of("kimi", "qwen")),
                "kimi 与 qwen 均应注册成功，实际: " + service.getProviderNames());
    }

    /** FR-008: 未注册 Provider 的调用报错必须点名 env 变量，给出修复路径。 */
    @Test
    void unregisteredProviderErrorNamesEnvVar() {
        DefaultProviderService service = new DefaultProviderService(twoProviderProps());
        LlmRequest request = new LlmRequest();
        request.setProviderName("ghost");
        request.setModel("any");
        request.setMessages(List.of());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.complete(request));
        assertTrue(e.getMessage().contains("ghost"), "报错应含 provider 名: " + e.getMessage());
        assertTrue(e.getMessage().contains("GHOST_API_KEY"), "报错应点名约定 env 变量: " + e.getMessage());
    }

    /** FR-008: 非法配置启动即失败，且一次性点名全部问题字段与修复指引。 */
    @Test
    void illegalConfigFailsStartupNamingEveryField() {
        ProviderProperties props = new ProviderProperties();
        ProviderProperties.Def unnamed = new ProviderProperties.Def();
        ProviderProperties.Def dup = new ProviderProperties.Def();
        dup.setName("kimi");
        dup.setBaseUrl("ftp://bad.example");
        dup.setApiKeyEnv("PATH");
        ProviderProperties.Def dupAgain = new ProviderProperties.Def();
        dupAgain.setName("kimi");
        dupAgain.setBaseUrl("https://api.moonshot.cn/v1");
        dupAgain.setApiKeyEnv("PATH");
        props.setProviders(List.of(unnamed, dup, dupAgain));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DefaultProviderService(props));
        String msg = e.getMessage();
        assertTrue(msg.contains("name"), "应点名 name 字段: " + msg);
        assertTrue(msg.contains("base-url"), "应点名 base-url 字段: " + msg);
        assertTrue(msg.contains("ftp://bad.example"), "应给出当前非法值: " + msg);
        assertTrue(msg.contains("重复"), "应报告重复定义: " + msg);
        assertTrue(msg.contains("共 5 处"), "应聚合报告全部 5 处问题（缺name/缺base-url/缺api-key-env/base-url协议非法/重名）: " + msg);
    }

    private static ProviderProperties twoProviderProps() {
        ProviderProperties props = new ProviderProperties();
        ProviderProperties.Def kimi = new ProviderProperties.Def();
        kimi.setName("kimi");
        kimi.setBaseUrl("https://api.moonshot.cn/v1");
        kimi.setApiKeyEnv("PATH");
        ProviderProperties.Def qwen = new ProviderProperties.Def();
        qwen.setName("qwen");
        qwen.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        qwen.setApiKeyEnv("PATH");
        props.setProviders(List.of(kimi, qwen));
        return props;
    }
}
