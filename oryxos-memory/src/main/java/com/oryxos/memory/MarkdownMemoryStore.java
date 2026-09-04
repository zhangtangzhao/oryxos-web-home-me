package com.oryxos.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Core-phase long-term memory backend (FR-018): a single shared
 * {@code <workspace>/memory/MEMORY.md} file. Entries are appended as Markdown
 * bullets with a timestamp prefix; recall is a case-insensitive substring scan
 * returning the matched lines (clarification #5). The MemoryScope parameter is
 * kept for the interface contract — core phase writes both scopes into the one
 * shared file.
 */
public class MarkdownMemoryStore implements LongTermMemoryStore {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Path memoryFile;

    public MarkdownMemoryStore(Path workspaceRoot) {
        this.memoryFile = workspaceRoot.resolve("memory").resolve("MEMORY.md");
    }

    @Override
    public void append(String content, MemoryScope scope) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        try {
            Files.createDirectories(memoryFile.getParent());
            String entry = "- [" + LocalDateTime.now().format(TS) + "] " + content.trim() + "\n";
            Files.writeString(memoryFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("写入长期记忆失败: " + memoryFile, e);
        }
    }

    @Override
    public String load() {
        if (!Files.exists(memoryFile)) {
            return "";
        }
        try {
            return Files.readString(memoryFile).trim();
        } catch (IOException e) {
            throw new IllegalStateException("读取长期记忆失败: " + memoryFile, e);
        }
    }

    @Override
    public String recallByKeyword(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String needle = query.trim().toLowerCase();
        StringBuilder hits = new StringBuilder();
        for (String line : load().split("\r?\n")) {
            if (line.toLowerCase().contains(needle)) {
                if (!hits.isEmpty()) {
                    hits.append('\n');
                }
                hits.append(line);
            }
        }
        return hits.toString();
    }

    public Path memoryFile() {
        return memoryFile;
    }
}
