package com.oryxos.core.agent;

import com.oryxos.core.Profile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads Agent definitions from .oryxos/agents/<name>/AGENT.md.
 * One directory = one Agent (constitution VII); the frontmatter is the only
 * runtime configuration source and is derived into a Profile (FR-002/003).
 * Invalid definitions fail fast with a clear message — never a half Profile.
 */
public class AgentLoader {

    private final Path agentsDir;
    private final Map<String, Profile> profiles = new LinkedHashMap<>();

    public AgentLoader(Path workspaceRoot) {
        this.agentsDir = workspaceRoot.resolve("agents");
        reload();
    }

    public synchronized void reload() {
        profiles.clear();
        if (!Files.isDirectory(agentsDir)) {
            return;
        }
        try (Stream<Path> dirs = Files.list(agentsDir)) {
            dirs.filter(Files::isDirectory).sorted().forEach(dir -> {
                Path agentMd = dir.resolve("AGENT.md");
                if (!Files.exists(agentMd)) {
                    return;
                }
                Profile profile = parse(agentMd, dir);
                profiles.put(profile.getName(), profile);
            });
        } catch (IOException e) {
            throw new IllegalStateException("扫描 Agent 目录失败: " + agentsDir, e);
        }
    }

    public Profile get(String name) {
        return profiles.get(name);
    }

    public Profile require(String name) {
        Profile p = profiles.get(name);
        if (p == null) {
            throw new IllegalArgumentException("Agent 不存在: " + name + "（可用: " + profiles.keySet() + "）");
        }
        return p;
    }

    /** First profile in alphabetical order — CLI default when no --profile is given. */
    public Profile first() {
        return profiles.values().stream().findFirst().orElse(null);
    }

    public List<Profile> list() {
        return new ArrayList<>(profiles.values());
    }

    public int count() {
        return profiles.size();
    }

    public Path agentDir(String name) {
        return agentsDir.resolve(name);
    }

    // ---- parsing ----

    private Profile parse(Path agentMd, Path dir) {
        String content;
        try {
            content = Files.readString(agentMd);
        } catch (IOException e) {
            throw new IllegalStateException("读取失败: " + agentMd, e);
        }

        String front = extractFrontmatter(content, agentMd);
        if (front == null || front.isBlank()) {
            throw new IllegalArgumentException(agentMd + ": 缺少 YAML frontmatter（需以 --- 开头并闭合）");
        }

        Map<String, Object> map;
        try {
            Object loaded = new Yaml().load(front);
            map = (loaded == null) ? new LinkedHashMap<>() : castMap(loaded, "frontmatter 根节点");
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(agentMd + ": frontmatter 必须是 YAML 映射");
        }

        Profile p = new Profile();
        String dirName = dir.getFileName().toString();

        Object name = map.get("name");
        if (!(name instanceof String n) || n.isBlank()) {
            throw new IllegalArgumentException(agentMd + ": 缺少必填字段 name");
        }
        if (!n.equals(dirName)) {
            throw new IllegalArgumentException(agentMd + ": name '" + n + "' 必须与目录名 '" + dirName + "' 一致");
        }
        p.setName(n);
        p.setDescription(str(map.get("description")));

        Map<String, Object> identity = optMap(map, "identity");
        if (!identity.isEmpty()) {
            Profile.Identity id = new Profile.Identity();
            id.setAgentName(str(identity.get("agent_name")));
            id.setPrompt(str(identity.get("prompt")));
            p.setIdentity(id);
        }

        Map<String, Object> provider = optMap(map, "provider");
        if (provider.isEmpty() || str(provider.get("name")) == null || str(provider.get("model")) == null) {
            throw new IllegalArgumentException(agentMd + ": 缺少必填字段 provider.name / provider.model");
        }
        Profile.ProviderRef ref = new Profile.ProviderRef();
        ref.setName(str(provider.get("name")));
        ref.setModel(str(provider.get("model")));
        Object temperature = provider.get("temperature");
        if (temperature instanceof Number num) {
            ref.setTemperature(num.floatValue());
        }
        p.setProvider(ref);

        p.setTools(strList(map.get("tools"), "tools"));
        p.setMcpServers(strList(map.get("mcp_servers"), "mcp_servers"));
        p.setKnowledgeBases(strList(map.get("knowledge_bases"), "knowledge_bases"));
        p.setBootstrap(strList(map.get("bootstrap"), "bootstrap"));

        List<Map<String, Object>> channels = mapList(map.get("channels"), "channels");
        if (!channels.isEmpty()) {
            List<Profile.ChannelRef> channelRefs = new ArrayList<>();
            for (Map<String, Object> c : channels) {
                Profile.ChannelRef cr = new Profile.ChannelRef();
                cr.setName(str(c.get("name")));
                Map<String, Object> cfg = optMap(c, "config");
                Map<String, String> stringCfg = new LinkedHashMap<>();
                cfg.forEach((k, v) -> stringCfg.put(k, v == null ? null : String.valueOf(v)));
                cr.setConfig(stringCfg);
                channelRefs.add(cr);
            }
            p.setChannels(channelRefs);
        }

        List<Map<String, Object>> schedules = mapList(map.get("schedules"), "schedules");
        if (!schedules.isEmpty()) {
            List<Profile.ScheduleDef> defs = new ArrayList<>();
            for (Map<String, Object> s : schedules) {
                Profile.ScheduleDef def = new Profile.ScheduleDef();
                def.setId(str(s.get("id")));
                def.setCron(str(s.get("cron")));
                def.setZone(str(s.get("zone")));
                def.setMessage(str(s.get("message")));
                if (def.getCron() == null) {
                    throw new IllegalArgumentException(agentMd + ": schedules[].cron 必填");
                }
                defs.add(def);
            }
            p.setSchedules(defs);
        }

        Map<String, Object> settings = optMap(map, "settings");
        Profile.Settings st = new Profile.Settings();
        Object maxIter = settings.get("max_iterations");
        if (maxIter instanceof Number mi) {
            if (mi.intValue() <= 0) {
                throw new IllegalArgumentException(agentMd + ": settings.max_iterations 必须为正整数");
            }
            st.setMaxIterations(mi.intValue());
        }
        Object maxTurns = settings.get("max_history_turns");
        if (maxTurns instanceof Number mt) {
            if (mt.intValue() <= 0) {
                throw new IllegalArgumentException(agentMd + ": settings.max_history_turns 必须为正整数");
            }
            st.setMaxHistoryTurns(mt.intValue());
        }
        Object timeout = settings.get("session_timeout_minutes");
        if (timeout instanceof Number to) {
            if (to.intValue() <= 0) {
                throw new IllegalArgumentException(agentMd + ": settings.session_timeout_minutes 必须为正整数");
            }
            st.setSessionTimeoutMinutes(to.intValue());
        }
        p.setSettings(st);

        return p;
    }

    /** Returns frontmatter text (without --- fences) or null if absent. */
    static String extractFrontmatter(String content, Path source) {
        String[] lines = content.split("\r?\n", -1);
        int i = 0;
        while (i < lines.length && lines[i].isBlank()) {
            i++;
        }
        if (i >= lines.length || !lines[i].trim().equals("---")) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i + 1; j < lines.length; j++) {
            if (lines[j].trim().equals("---")) {
                return sb.toString();
            }
            sb.append(lines[j]).append('\n');
        }
        throw new IllegalArgumentException(source + ": frontmatter 未闭合（缺少结束 ---）");
    }

    /** Splits off the frontmatter and returns the Markdown body. */
    public static String extractBody(String content) {
        String[] lines = content.split("\r?\n", -1);
        int i = 0;
        while (i < lines.length && lines[i].isBlank()) {
            i++;
        }
        if (i < lines.length && lines[i].trim().equals("---")) {
            for (int j = i + 1; j < lines.length; j++) {
                if (lines[j].trim().equals("---")) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = j + 1; k < lines.length; k++) {
                        sb.append(lines[k]).append('\n');
                    }
                    return sb.toString().trim();
                }
            }
            return "";
        }
        return content.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o, String what) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException(what + " 必须是 YAML 映射");
        }
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> optMap(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        if (v == null) {
            return Map.of();
        }
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("字段 " + key + " 必须是映射");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    private static List<Map<String, Object>> mapList(Object v, String key) {
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            throw new IllegalArgumentException("字段 " + key + " 必须是列表");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("字段 " + key + " 的每一项必须是映射");
            }
            result.add(castMap(item, key + " 项"));
        }
        return result;
    }

    private static List<String> strList(Object v, String key) {
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            throw new IllegalArgumentException("字段 " + key + " 必须是字符串列表");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String s)) {
                throw new IllegalArgumentException("字段 " + key + " 的每一项必须是字符串");
            }
            result.add(s);
        }
        return result;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
