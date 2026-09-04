package com.oryxos.tool.mcp;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads config/mcp_servers.yaml (data-model §4), connects stdio/SSE servers
 * via the official MCP Java SDK, and registers each remote tool as an
 * {@link McpTool} named {@code <server>__<tool>} in the shared ToolRegistry —
 * so calls flow through the same audit chain as built-ins (research R-5,
 * FR-015/016). A failing server is logged and skipped, never blocking startup.
 */
@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
    private static final Pattern ENV_REF = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private final ToolRegistry registry;
    private final String configLocation;
    private final List<McpSyncClient> clients = new ArrayList<>();

    public McpClientService(ToolRegistry registry,
                            @Value("${oryxos.mcp.servers-file:config/mcp_servers.yaml}") String configLocation) {
        this.registry = registry;
        this.configLocation = configLocation;
        connectAll();
    }

    private void connectAll() {
        for (McpServerDefinition server : loadDefinitions()) {
            try {
                McpSyncClient client = connect(server);
                int count = registerTools(server, client);
                clients.add(client);
                log.info("MCP server [{}] 已连接，注册 {} 个工具", server.getName(), count);
            } catch (Exception e) {
                log.warn("MCP server [{}] 连接失败，已跳过（不阻断启动）: {}", server.getName(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    List<McpServerDefinition> loadDefinitions() {
        Path file = Path.of(configLocation);
        if (!Files.isRegularFile(file)) {
            log.info("未找到 MCP 配置（{}），跳过 MCP 工具加载", configLocation);
            return List.of();
        }
        try {
            Map<String, Object> root = new Yaml().load(Files.readString(file));
            Object servers = root == null ? null : root.get("servers");
            if (!(servers instanceof List<?> list)) {
                log.warn("MCP 配置缺少 servers 列表: {}", configLocation);
                return List.of();
            }
            List<McpServerDefinition> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                McpServerDefinition def = new McpServerDefinition();
                def.setName(resolveEnv(String.valueOf(raw.get("name"))));
                def.setTransport(raw.get("transport") == null ? "stdio"
                        : resolveEnv(String.valueOf(raw.get("transport"))));
                def.setCommand(raw.get("command") == null ? null : resolveEnv(String.valueOf(raw.get("command"))));
                def.setUrl(raw.get("url") == null ? null : resolveEnv(String.valueOf(raw.get("url"))));
                if (raw.get("args") instanceof List<?> args) {
                    def.setArgs(args.stream().map(a -> resolveEnv(String.valueOf(a))).toList());
                }
                if (raw.get("env") instanceof Map<?, ?> envMap) {
                    Map<String, String> env = new java.util.LinkedHashMap<>();
                    envMap.forEach((k, v) -> env.put(String.valueOf(k), resolveEnv(String.valueOf(v))));
                    def.setEnv(env);
                }
                if (def.getName() == null || def.getName().isBlank()) {
                    log.warn("MCP server 缺少 name，已跳过: {}", raw);
                    continue;
                }
                result.add(def);
            }
            return result;
        } catch (IOException e) {
            log.warn("MCP 配置读取失败（{}）: {}", configLocation, e.getMessage());
            return List.of();
        }
    }

    private McpSyncClient connect(McpServerDefinition server) {
        McpClient.SyncSpec spec = McpClient.sync(transport(server))
                .clientInfo(new McpSchema.Implementation("oryxos", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(30));
        McpSyncClient client = spec.build();
        client.initialize();
        return client;
    }

    private io.modelcontextprotocol.spec.McpClientTransport transport(McpServerDefinition server) {
        if ("http".equalsIgnoreCase(server.getTransport()) || "sse".equalsIgnoreCase(server.getTransport())) {
            if (server.getUrl() == null || server.getUrl().isBlank()) {
                throw new IllegalArgumentException("http 传输缺少 url: " + server.getName());
            }
            return HttpClientSseClientTransport.builder(server.getUrl()).build();
        }
        if (server.getCommand() == null || server.getCommand().isBlank()) {
            throw new IllegalArgumentException("stdio 传输缺少 command: " + server.getName());
        }
        ServerParameters params = ServerParameters.builder(server.getCommand())
                .args(server.getArgs())
                .env(server.getEnv())
                .build();
        return new StdioClientTransport(params);
    }

    private int registerTools(McpServerDefinition server, McpSyncClient client) {
        McpSchema.ListToolsResult tools = client.listTools();
        int count = 0;
        for (McpSchema.Tool tool : tools.tools()) {
            String localName = server.toolPrefix() + McpServerDefinition.sanitize(tool.name());
            String schemaJson = schemaToJson(tool.inputSchema());
            OryxTool oryxTool = new McpTool(client, localName,
                    tool.description() == null ? "" : tool.description(), schemaJson);
            registry.register(oryxTool);
            count++;
        }
        return count;
    }

    private static String schemaToJson(McpSchema.JsonSchema schema) {
        try {
            Map<String, Object> map = Map.of(
                    "type", schema.type() == null ? "object" : schema.type(),
                    "properties", schema.properties() == null ? Map.of() : schema.properties(),
                    "required", schema.required() == null ? List.of() : schema.required());
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{\"type\":\"object\"}";
        }
    }

    /** ${VAR} → environment value; unresolved refs become empty (never leak the literal). */
    static String resolveEnv(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = ENV_REF.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement = System.getenv(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public List<McpSyncClient> connectedClients() {
        return List.copyOf(clients);
    }
}
