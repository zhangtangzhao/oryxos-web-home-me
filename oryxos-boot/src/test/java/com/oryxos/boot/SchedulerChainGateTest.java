package com.oryxos.boot;

import com.oryxos.core.llm.LlmClient;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.storage.LlmCallRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quickstart scenario 9 mechanical half (SC-008): an AGENT.md schedule is
 * registered into the scheduler at start(), and triggerNow() (手动补跑) fires
 * through the same SessionService + AgentService chain — the scheduler
 * session (scheduler:scheduler:<profile>) is persisted and its llm_calls rows
 * carry the scheduler session id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"oryxos.root=target/ws-sched"})
class SchedulerChainGateTest {

    private static final String AGENT = "dailyw";

    @Autowired
    AgentScheduler scheduler;
    @Autowired
    LlmCallRepository llmRepo;

    @BeforeAll
    static void createWorkspace() throws Exception {
        Path root = Path.of("target/ws-sched");
        Files.createDirectories(root.resolve("agents").resolve(AGENT));
        Files.writeString(root.resolve("agents").resolve(AGENT).resolve("AGENT.md"), String.join("\n",
                "---",
                "name: " + AGENT,
                "description: scheduler gate agent",
                "provider:",
                "  name: deepseek",
                "  model: deepseek-chat",
                "tools: []",
                "bootstrap: []",
                "schedules:",
                "  - id: t1",
                "    cron: \"0 0 7 * * ?\"",
                "    zone: Asia/Shanghai",
                "    message: 每日定时消息",
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
                result.setContent("SCHED_OK");
                result.setDurationMs(1);
                return result;
            };
        }
    }

    @Test
    void scheduleIsRegisteredAndManualCatchUpRunsSameChain() {
        scheduler.start();
        assertTrue(scheduler.isRunning());
        assertEquals(1, scheduler.taskCount());

        assertEquals(1, scheduler.triggerNow(AGENT, "t1"));

        String schedulerSession = "scheduler-scheduler-" + AGENT;
        assertTrue(llmRepo.findBySessionId(schedulerSession).size() >= 1,
                "补跑必须产生 llm_calls 审计记录（同一链路）");
    }
}
