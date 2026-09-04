package com.oryxos.cli;

import com.oryxos.core.workspace.WorkspaceInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * oryxos serve — start the HTTP API server (FR-020). The launcher boots the
 * Spring context with a servlet web server before dispatching here; this
 * command only validates the workspace and then blocks so the REST API stays
 * resident until Ctrl+C. Scheduler is NOT started (use gateway for that).
 */
@Command(name = "serve", description = "Start HTTP API server (Spring MVC)")
public class ServeCommand implements Callable<Integer> {

    @Option(names = {"--port"}, description = "Server port (default: 8080)")
    private int port = 8080;

    @Autowired
    private Environment environment;

    @Override
    public Integer call() throws InterruptedException {
        if (!WorkspaceInitializer.isInitialized(WorkspaceInitializer.resolveRoot())) {
            System.err.println("未找到 OryxOS 工作区。请先执行: oryxos init");
            return 1;
        }
        int actualPort = Integer.parseInt(environment.getProperty("local.server.port", String.valueOf(port)));
        System.out.println("OryxOS API server 已启动: http://localhost:" + actualPort + "/api/v1/");
        System.out.println("按 Ctrl+C 停止。");
        new CountDownLatch(1).await();
        return 0;
    }
}
