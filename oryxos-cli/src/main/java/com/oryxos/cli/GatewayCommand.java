package com.oryxos.cli;

import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.workspace.WorkspaceInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * oryxos gateway — resident daemon: REST API + cron scheduler (FR-020/021).
 * The launcher already started the servlet web server; here the scheduler is
 * started over AGENT.md schedules and the process blocks until Ctrl+C.
 */
@Command(name = "gateway", description = "Start resident daemon (REST API + scheduler)")
public class GatewayCommand implements Callable<Integer> {

    /** Declared for Picocli acceptance; the launcher applies it before boot. */
    @Option(names = "--port", description = "HTTP port (applied by launcher before Spring starts)", defaultValue = "8080")
    private int port;

    @Autowired
    private AgentScheduler agentScheduler;

    @Autowired
    private Environment environment;

    @Override
    public Integer call() throws InterruptedException {
        if (!WorkspaceInitializer.isInitialized(WorkspaceInitializer.resolveRoot())) {
            System.err.println("未找到 OryxOS 工作区。请先执行: oryxos init");
            return 1;
        }
        agentScheduler.start();
        String port = environment.getProperty("local.server.port", "8080");
        System.out.println("OryxOS Gateway 已启动: http://localhost:" + port + "/api/v1/");
        System.out.println("调度器已启动，定时任务 " + agentScheduler.taskCount() + " 个。按 Ctrl+C 停止。");
        new CountDownLatch(1).await();
        return 0;
    }
}
