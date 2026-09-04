package com.oryxos.core.agent;

import com.oryxos.core.Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Assembles context pieces injected into the system prompt, in research R-7 order:
 * AGENT.md body -> AGENTS.md -> SOUL.md -> USER.md -> bound Skill metadata -> MEMORY.md view.
 * <p>
 * Skill binding is expressed by a relative symlink/dir under the agent directory
 * pointing to a shared skill entity; only its name/description metadata is injected —
 * the body is read on demand via read_file (constitution VIII: Skill != Tool).
 */
public class ContextLoader {

    /** Long-term memory view injected into the system prompt is truncated to this size (FR-018). */
    public static final int MEMORY_VIEW_LIMIT = 4000;

    private final Path workspaceRoot;

    public ContextLoader(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String agentBody(Profile profile) {
        Path agentMd = workspaceRoot.resolve("agents").resolve(profile.getName()).resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            return "";
        }
        try {
            return AgentLoader.extractBody(Files.readString(agentMd));
        } catch (IOException e) {
            throw new IllegalStateException("读取 AGENT.md 正文失败: " + agentMd, e);
        }
    }

    /** Bootstrap files per profile policy: null/empty list = all three enabled (contract §4). */
    public String bootstrapText(Profile profile) {
        List<String> enabled = profile.getBootstrap();
        StringBuilder sb = new StringBuilder();
        for (String name : List.of("AGENTS.md", "SOUL.md", "USER.md")) {
            if (enabled != null && !enabled.isEmpty() && !enabled.contains(name)) {
                continue;
            }
            String content = readFileQuietly(workspaceRoot.resolve(name));
            if (content != null && !content.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("## ").append(name).append("\n\n").append(content.trim());
            }
        }
        return sb.toString();
    }

    /** Metadata-only view of skills bound to the agent via its skills/ directory. */
    public String skillMetadata(Profile profile) {
        Path skillsDir = workspaceRoot.resolve("agents").resolve(profile.getName()).resolve("skills");
        if (!Files.isDirectory(skillsDir)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> links = Files.list(skillsDir)) {
            links.filter(p -> Files.isDirectory(p) || Files.isSymbolicLink(p)).sorted().forEach(skillDir -> {
                String name = skillDir.getFileName().toString();
                String description = "";
                Path skillMd = skillDir.resolve("SKILL.md");
                if (Files.exists(skillMd)) {
                    try {
                        description = parseDescription(Files.readString(skillMd));
                    } catch (IOException ignored) {
                        // description stays empty
                    }
                }
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append("- ").append(name);
                if (!description.isBlank()) {
                    sb.append(": ").append(description);
                }
                sb.append("（正文按需读取: ").append(skillMd).append("）");
            });
        } catch (IOException e) {
            return "";
        }
        return sb.toString();
    }

    /**
     * Long-term memory view injected into the system prompt. MVP reads MEMORY.md
     * directly with the FR-018 truncation; US3 replaces this with the memory module.
     */
    public String memoryView() {
        String content = readFileQuietly(workspaceRoot.resolve("memory").resolve("MEMORY.md"));
        if (content == null || content.isBlank()) {
            return "";
        }
        return truncate(content.trim(), MEMORY_VIEW_LIMIT);
    }

    public static String truncate(String s, int limit) {
        if (s.length() <= limit) {
            return s;
        }
        return s.substring(0, limit) + "\n…（已截断）";
    }

    private static String parseDescription(String skillMd) {
        String[] lines = skillMd.split("\r?\n");
        boolean inFrontmatter = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("---")) {
                if (inFrontmatter) {
                    break;
                }
                inFrontmatter = true;
                continue;
            }
            if (inFrontmatter && trimmed.startsWith("description:")) {
                return trimmed.substring("description:".length()).trim().replaceAll("^[\"']|[\"']$", "");
            }
        }
        return "";
    }

    private static String readFileQuietly(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return null;
        }
    }
}
