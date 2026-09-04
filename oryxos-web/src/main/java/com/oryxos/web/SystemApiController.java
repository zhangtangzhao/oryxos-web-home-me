package com.oryxos.web;

import com.oryxos.core.agent.AgentLoader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** health / info (contract §9/§10, SC-009). */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {

    private static final Instant BOOT_TIME = Instant.now();
    private static final String VERSION = "1.0.0-SNAPSHOT";

    private final AgentLoader agentLoader;
    private final Path workspaceRoot;
    private final ObjectProvider<DataSource> dataSource;

    @Autowired
    public SystemApiController(AgentLoader agentLoader, Path workspaceRoot,
                               ObjectProvider<DataSource> dataSource) {
        this.agentLoader = agentLoader;
        this.workspaceRoot = workspaceRoot;
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("workspace", workspaceRoot.toString());
        data.put("agents_loaded", agentLoader.count());
        data.put("db_ok", checkDb());
        return ApiResponse.ok(data);
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "oryxos");
        data.put("version", VERSION);
        data.put("java_version", System.getProperty("java.version"));
        data.put("uptime_seconds", Duration.between(BOOT_TIME, Instant.now()).getSeconds());
        return ApiResponse.ok(data);
    }

    private boolean checkDb() {
        DataSource ds = dataSource.getIfAvailable();
        if (ds == null) {
            return false;
        }
        try (Connection conn = ds.getConnection(); ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
}
