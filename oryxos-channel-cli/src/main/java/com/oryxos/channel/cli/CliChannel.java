package com.oryxos.channel.cli;

import com.oryxos.core.AgentService;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.audit.AuditLog;
import com.oryxos.core.audit.ToolCallRecord;
import com.oryxos.core.session.SessionService;
import com.oryxos.core.workspace.WorkspaceInitializer;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CLI channel: interactive multi-turn conversation over the same AgentService
 * chain used by every other trigger source (FR-022/023, FR-029 CLI side).
 */
@Component
public class CliChannel {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final AgentLoader agentLoader;
    private final SessionService sessionService;
    private final AgentService agentService;
    private final AuditLog auditLog;

    public CliChannel(AgentLoader agentLoader, SessionService sessionService,
                      AgentService agentService, AuditLog auditLog) {
        this.agentLoader = agentLoader;
        this.sessionService = sessionService;
        this.agentService = agentService;
        this.auditLog = auditLog;
    }

    /** @return process exit code. */
    public int run(String profileArg, String singleMessage) {
        if (!WorkspaceInitializer.isInitialized(WorkspaceInitializer.resolveRoot())) {
            System.err.println("未找到 OryxOS 工作区。请先执行: oryxos init");
            return 1;
        }
        if (agentLoader.count() == 0) {
            System.err.println("没有可用 Agent。请先执行: oryxos profile create <name>");
            return 1;
        }

        Profile profile = resolveProfile(profileArg);
        if (profile == null) {
            System.err.println("Agent 不存在: " + profileArg + "（可用: " + names() + "）");
            return 1;
        }

        String userId = System.getProperty("user.name", "user");
        Session session = sessionService.getOrCreate("cli", userId, profile.getName());

        if (singleMessage != null && !singleMessage.isBlank()) {
            try {
                System.out.println(agentService.process(session, singleMessage));
            } catch (Exception e) {
                System.err.println("处理失败: " + e.getMessage());
                return 1;
            }
            return 0;
        }

        System.out.println("OryxOS Chat — Agent [" + profile.getName() + "]，会话 " + session.getSessionId());
        System.out.println("交互命令: /tools 查看工具调用记录, /exit 退出");
        System.out.println();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                System.out.flush();
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String input = line.trim();
                if (input.isEmpty()) {
                    continue;
                }
                if ("/exit".equals(input) || "/quit".equals(input)) {
                    System.out.println("再见。");
                    break;
                }
                if ("/tools".equals(input)) {
                    printToolInvocations(session.getSessionId());
                    continue;
                }
                try {
                    System.out.println(agentService.process(session, input));
                } catch (Exception e) {
                    System.err.println("处理失败: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("输入读取失败: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    private Profile resolveProfile(String profileArg) {
        if (profileArg == null || profileArg.isBlank() || "default".equals(profileArg)) {
            return agentLoader.first();
        }
        return agentLoader.get(profileArg);
    }

    private void printToolInvocations(String sessionId) {
        List<ToolCallRecord> records = auditLog.toolInvocations(sessionId);
        if (records.isEmpty()) {
            System.out.println("（本会话暂无工具调用记录）");
            return;
        }
        System.out.println("工具调用记录（最新在前）:");
        for (ToolCallRecord r : records) {
            String status = r.success() ? "OK" : "BLOCKED/FAIL";
            System.out.printf("  [%s] %-12s %s  %dms%n",
                    r.createdAt() == null ? "-" : TS.format(r.createdAt().atZone(ZoneId.systemDefault())),
                    r.toolName(), status, r.durationMs());
            if (!r.success() && r.errorMessage() != null) {
                System.out.println("      错误: " + r.errorMessage());
            }
        }
    }

    private List<String> names() {
        return agentLoader.list().stream().map(Profile::getName).toList();
    }
}
