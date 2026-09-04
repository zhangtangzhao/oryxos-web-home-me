package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * shell built-in tool: the command must pass the shell command whitelist
 * BEFORE execution (FR-012/013). Runs through the platform shell with a hard
 * timeout; output truncated for the conversation history.
 */
public class ShellTool implements OryxTool {

    public static final int MAX_OUTPUT_LENGTH = 8000;
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int MAX_TIMEOUT_SECONDS = 120;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Sandbox sandbox;

    public ShellTool(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String getName() {
        return "shell";
    }

    @Override
    public String getDescription() {
        return "执行一条 Shell 命令并返回输出（受命令白名单与超时限制）";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("command").put("type", "string").put("description", "要执行的 Shell 命令");
        properties.putObject("timeout_seconds").put("type", "integer")
                .put("description", "超时秒数，默认 " + DEFAULT_TIMEOUT_SECONDS + "，上限 " + MAX_TIMEOUT_SECONDS);
        schema.putArray("required").add("command");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String command = input.path("command").asText(null);
        if (command == null || command.isBlank()) {
            return ToolResult.failure("缺少必填参数 command", false);
        }
        int timeout = input.path("timeout_seconds").asInt(DEFAULT_TIMEOUT_SECONDS);
        timeout = Math.min(Math.max(timeout, 1), MAX_TIMEOUT_SECONDS);

        // 白名单校验必须是第一道闸（FR-013）
        sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, command);

        List<String> wrapper = isWindows() ? List.of("cmd", "/c", command) : List.of("sh", "-c", command);
        Process process = null;
        try {
            process = new ProcessBuilder(wrapper)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    output.append(buffer, 0, n);
                    if (output.length() > MAX_OUTPUT_LENGTH) {
                        break;
                    }
                }
            }
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("命令超时（" + timeout + "s），已终止", false);
            }
            int code = process.exitValue();
            String text = output.toString();
            if (text.length() > MAX_OUTPUT_LENGTH) {
                text = text.substring(0, MAX_OUTPUT_LENGTH) + "\n…(truncated)";
            }
            String header = "exit=" + code + "\n";
            return ToolResult.success(header + (text.isBlank() ? "（无输出）" : text));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return ToolResult.failure("命令执行被中断", false);
        } catch (Exception e) {
            return ToolResult.failure("命令执行失败: " + e.getMessage(), true);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
