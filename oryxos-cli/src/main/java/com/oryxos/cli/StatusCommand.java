package com.oryxos.cli;

import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.workspace.WorkspaceInitializer;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * oryxos status — 工作区路径、Agent 数量、已配置 Provider、会话统计
 * （active/archived）、数据库与调度器状态（contracts/cli.md）。轻命令：
 * 不起 Spring，会话/定时任务统计直查 SQLite。
 */
@Command(name = "status", description = "Show workspace and configuration status")
public class StatusCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        Path root = WorkspaceInitializer.resolveRoot();
        if (!WorkspaceInitializer.isInitialized(root)) {
            System.err.println("未找到 OryxOS 工作区。请先执行: oryxos init");
            return 1;
        }

        System.out.println("OryxOS Status");
        System.out.println("=============");
        System.out.println("Workspace: " + root.toAbsolutePath());

        int agentCount = new AgentLoader(root).list().size();
        System.out.println("Agents: " + agentCount);

        printProviders();

        printDatabaseAndScheduler(root.resolve("oryxos.db"));
        return 0;
    }

    private void printProviders() {
        Map<String, Object> root = ProviderCommand.ListCommand.loadYaml(
                ProviderCommand.ListCommand.classpathApplicationYaml());
        List<Map<String, Object>> providers = root == null
                ? List.of() : ProviderCommand.ListCommand.flattenProviders(root);
        if (providers.isEmpty()) {
            System.out.println("Providers: (配置中未定义 oryxos.providers)");
            return;
        }
        System.out.println("Providers:");
        for (Map<String, Object> p : providers) {
            String name = String.valueOf(p.get("name"));
            String env = p.get("api-key-env") == null ? "" : String.valueOf(p.get("api-key-env"));
            boolean present = !env.isEmpty() && System.getenv(env) != null && !System.getenv(env).isBlank();
            System.out.printf("  - %-12s key_env=%-20s [%s]%n", name, env,
                    present ? "✓" : "✗ 未设置");
        }
    }

    private void printDatabaseAndScheduler(Path db) {
        if (!Files.isRegularFile(db)) {
            System.out.println("Database: 未创建（serve/gateway/chat 首次运行时生成）");
            System.out.println("Scheduler: 未运行（由 gateway 进程承载）");
            return;
        }
        long active = 0;
        long archived = 0;
        long scheduledTasks = -1;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT status, COUNT(*) FROM sessions GROUP BY status");
            while (rs.next()) {
                if ("archived".equals(rs.getString(1))) {
                    archived = rs.getLong(2);
                } else {
                    active += rs.getLong(2);
                }
            }
            try {
                ResultSet tasks = st.executeQuery(
                        "SELECT COUNT(*) FROM scheduled_tasks");
                if (tasks.next()) {
                    scheduledTasks = tasks.getLong(1);
                }
            } catch (Exception tableMissing) {
                // scheduled_tasks 由 gateway 首次注册任务时建表，serve-only 部署可无此表
            }
        } catch (Exception e) {
            System.out.println("Database: 存在但查询失败（" + e.getMessage() + "）");
            return;
        }
        System.out.println("Database: ok（active=" + active + " archived=" + archived + "）");
        System.out.println("Scheduler: " + (scheduledTasks < 0
                ? "定时任务表尚未创建（gateway 首次注册时生成）"
                : "已注册定时任务 " + scheduledTasks + " 个（gateway 进程运行时生效）"));
    }
}
