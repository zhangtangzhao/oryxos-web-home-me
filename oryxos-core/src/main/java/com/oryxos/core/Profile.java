package com.oryxos.core;

import java.util.List;
import java.util.Map;

/**
 * Runtime profile derived from AGENT.md frontmatter by AgentLoader.deriveProfile().
 * Defines how an agent runs: which Provider, model, tools, channels, schedules, etc.
 */
public class Profile {

    private String name;
    private String description;
    private Identity identity;
    private ProviderRef provider;
    private List<String> tools;
    private List<String> mcpServers;
    private List<ChannelRef> channels;
    private List<ScheduleDef> schedules;
    private List<String> bootstrap;
    private Settings settings;

    public static class Identity {
        private String agentName;
        private String prompt;
        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
    }

    public static class ProviderRef {
        private String name;
        private String model;
        private Float temperature;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Float getTemperature() { return temperature; }
        public void setTemperature(Float temperature) { this.temperature = temperature; }
    }

    public static class ChannelRef {
        private String name;
        private Map<String, String> config;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, String> getConfig() { return config; }
        public void setConfig(Map<String, String> config) { this.config = config; }
    }

    public static class ScheduleDef {
        private String id;
        private String cron;
        private String zone;
        private String message;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class Settings {
        private int maxIterations = 10;
        private int maxHistoryTurns = 20;
        private int sessionTimeoutMinutes = 30;
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        public int getMaxHistoryTurns() { return maxHistoryTurns; }
        public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }
        public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
        public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }
    public ProviderRef getProvider() { return provider; }
    public void setProvider(ProviderRef provider) { this.provider = provider; }
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    public List<String> getMcpServers() { return mcpServers; }
    public void setMcpServers(List<String> mcpServers) { this.mcpServers = mcpServers; }
    public List<ChannelRef> getChannels() { return channels; }
    public void setChannels(List<ChannelRef> channels) { this.channels = channels; }
    public List<ScheduleDef> getSchedules() { return schedules; }
    public void setSchedules(List<ScheduleDef> schedules) { this.schedules = schedules; }
    public List<String> getBootstrap() { return bootstrap; }
    public void setBootstrap(List<String> bootstrap) { this.bootstrap = bootstrap; }
    public Settings getSettings() { return settings; }
    public void setSettings(Settings settings) { this.settings = settings; }
}
