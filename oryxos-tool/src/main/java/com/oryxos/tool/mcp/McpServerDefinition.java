package com.oryxos.tool.mcp;

import java.util.List;
import java.util.Map;

/**
 * One server entry of config/mcp_servers.yaml (data-model §4):
 * {name, transport(stdio|http), command/url, args, env}. Environment
 * references of the form ${VAR} are resolved from environment variables at
 * load time (constitution VI: secrets never in plaintext).
 */
public class McpServerDefinition {

    private String name;
    private String transport = "stdio";
    private String command;
    private String url;
    private List<String> args = List.of();
    private Map<String, String> env = Map.of();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args; }
    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    /** Registered tool-name prefix: <server>__<tool> (OpenAI function names allow no dots). */
    public String toolPrefix() {
        return sanitize(name) + "__";
    }

    static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
