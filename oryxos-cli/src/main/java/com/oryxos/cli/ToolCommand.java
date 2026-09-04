package com.oryxos.cli;

import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * oryxos tool list — list registered tools with origin and whitelist summary
 * (contracts/cli.md). Stays a LIGHT command (sub-second, no Spring): built-ins
 * are compile-time known, the whitelist summary is read from
 * config/application.yml, and MCP entries come from config/mcp_servers.yaml.
 */
@Command(name = "tool", description = "Manage tools",
         subcommands = ToolCommand.ListCommand.class)
public class ToolCommand {

    /** name / description of every built-in tool (keep in sync with ToolModuleConfiguration + MemoryToolRegistrar). */
    private static final List<String[]> BUILTINS = List.of(
            new String[]{"read_file", "读取文件内容（路径白名单）"},
            new String[]{"write_file", "写入文件内容（路径白名单）"},
            new String[]{"list_dir", "列出目录条目（路径白名单）"},
            new String[]{"shell", "执行 Shell 命令（命令白名单 + 超时）"},
            new String[]{"http_get", "HTTP GET 请求（域名白名单）"},
            new String[]{"http_post", "HTTP POST 请求（域名白名单）"},
            new String[]{"notify", "Webhook 通知推送（域名白名单 + 审计）"},
            new String[]{"save_memory", "保存长期记忆（MEMORY.md + 审计）"},
            new String[]{"recall_memory", "按关键词检索长期记忆（大小写不敏感）"}
    );

    @Command(name = "list", description = "List available tools")
    public static class ListCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Available Tools:");
            for (String[] tool : BUILTINS) {
                System.out.printf("  - %-14s [%s] %s%n", tool[0], "builtin", tool[1]);
            }

            Map<?, ?> mcpRoot = readYaml("config/mcp_servers.yaml");
            if (mcpRoot != null && mcpRoot.get("servers") instanceof List<?> servers && !servers.isEmpty()) {
                System.out.println();
                System.out.println("MCP Servers（连接后其工具以 <server>__<tool> 名称注册）:");
                for (Object item : servers) {
                    if (item instanceof Map<?, ?> server) {
                        Object transport = server.get("transport");
                        Object endpoint = server.containsKey("url") ? server.get("url") : server.get("command");
                        System.out.printf("  - %-14s [%s] %s -> %s%n",
                                String.valueOf(server.get("name")), "mcp",
                                transport == null ? "stdio" : String.valueOf(transport),
                                endpoint == null ? "-" : String.valueOf(endpoint));
                    }
                }
            }

            System.out.println();
            System.out.println("沙箱白名单摘要: " + sandboxSummary());
            return 0;
        }

        private String sandboxSummary() {
            Map<?, ?> root = readYaml("config/application.yml");
            if (root == null || !(root.get("oryxos") instanceof Map<?, ?> oryxos)
                    || !(oryxos.get("sandbox") instanceof Map<?, ?> sandbox)) {
                return "未找到 config/application.yml（使用内置默认：路径=工作区，命令/域名=内置清单）";
            }
            int paths = count(sandbox.get("file"), "allowed-paths");
            int commands = count(sandbox.get("shell"), "allowed-commands");
            Object domains = sandbox.get("http") instanceof Map<?, ?> http
                    && http.get("allowed-domains") instanceof List<?> list ? list : List.of();
            String domainText = domains instanceof List<?> list && list.contains("*")
                    ? "*（全部）" : String.valueOf(domains instanceof List<?> l ? l.size() : 0);
            return "paths=" + paths + ", commands=" + commands + ", domains=" + domainText;
        }

        private static int count(Object category, String key) {
            if (category instanceof Map<?, ?> map && map.get(key) instanceof List<?> list) {
                return list.size();
            }
            return 0;
        }

        private static Map<?, ?> readYaml(String location) {
            Path file = Path.of(location);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            try {
                return new Yaml().load(Files.readString(file));
            } catch (Exception e) {
                System.err.println("配置解析失败 " + location + ": " + e.getMessage());
                return null;
            }
        }
    }
}
