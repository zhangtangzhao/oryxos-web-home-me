package com.oryxos.core.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Idempotent workspace bootstrap: creates the .oryxos/ directory tree and
 * the three Bootstrap files. Existing files are never overwritten (FR-001).
 */
public final class WorkspaceInitializer {

    public static final String[] SUBDIRS = {"agents", "skills", "memory", "output", "sessions", "logs", "kb"};
    public static final String[] BOOTSTRAP_FILES = {"AGENTS.md", "SOUL.md", "USER.md"};

    private WorkspaceInitializer() {}

    /** Resolution order: -Doryxos.root system property > ORYXOS_ROOT env var > ./.oryxos */
    public static Path resolveRoot() {
        String sys = System.getProperty("oryxos.root");
        if (sys != null && !sys.isBlank()) {
            return Paths.get(sys);
        }
        String env = System.getenv("ORYXOS_ROOT");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        return Paths.get(".oryxos");
    }

    public static boolean isInitialized(Path root) {
        return Files.isDirectory(root);
    }

    /** @return one line per created/skipped item, in creation order. */
    public static List<String> initialize(Path root) throws IOException {
        List<String> report = new ArrayList<>();
        Files.createDirectories(root);

        for (String sub : SUBDIRS) {
            Path dir = root.resolve(sub);
            if (Files.isDirectory(dir)) {
                report.add("Skipped (exists): " + root.relativize(dir));
            } else {
                Files.createDirectories(dir);
                report.add("Created: " + root.relativize(dir));
            }
        }

        for (String name : BOOTSTRAP_FILES) {
            Path file = root.resolve(name);
            if (Files.exists(file)) {
                report.add("Skipped (exists): " + name);
            } else {
                Files.writeString(file, templateFor(name));
                report.add("Created: " + name);
            }
        }
        return report;
    }

    private static String templateFor(String name) {
        return switch (name) {
            case "AGENTS.md" -> "# AGENTS.md — 项目级 Agent 行为说明\n\n" +
                    "在此描述本工作区下所有 Agent 共同遵守的行为约定。\n" +
                    "本文件会注入每个 Agent 的 system prompt。\n";
            case "SOUL.md" -> "# SOUL.md — 默认 Agent 人格\n\n" +
                    "在此定义默认人格。Agent frontmatter 的 identity.prompt 优先于本文件。\n";
            case "USER.md" -> "# USER.md — 用户偏好\n\n" +
                    "在此描述用户偏好（语言、称呼、回复风格等）。\n";
            default -> "";
        };
    }
}
