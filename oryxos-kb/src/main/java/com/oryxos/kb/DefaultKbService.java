package com.oryxos.kb;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 知识库管理门面（US1）：create/list/show/delete/addDocument/ingest。
 * 名称规则 [a-z0-9][a-z0-9_-]{0,63}；文档落 <root>/kb/<name>/docs/。
 */
public class DefaultKbService {

    public static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "markdown", "txt");

    private final KbStore store;
    private final KbIngestService ingestService;
    private final Path workspaceRoot;

    public DefaultKbService(KbStore store, KbIngestService ingestService, Path workspaceRoot) {
        this.store = store;
        this.ingestService = ingestService;
        this.workspaceRoot = workspaceRoot;
    }

    public KbRecord create(String name, String description) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "非法知识库名称: " + name + "（需匹配 [a-z0-9][a-z0-9_-]{0,63}）");
        }
        KbRecord record = store.createKb(name, description);
        try {
            Files.createDirectories(docsDir(name));
        } catch (IOException e) {
            throw new UncheckedIOException("创建文档目录失败: " + docsDir(name), e);
        }
        return record;
    }

    public List<KbRecord> list() {
        return store.listKbs();
    }

    public KbRecord require(String name) {
        return store.findKb(name).orElseThrow(() -> new KbNotFoundException(name));
    }

    public List<KbDocumentRecord> documents(String name) {
        require(name);
        return store.listDocuments(name);
    }

    public void delete(String name) {
        require(name);
        store.deleteKb(name);
        deleteRecursive(kbDir(name));
    }

    /** 复制源文件入 docs/ 并记 pending（重名 → KB_CONFLICT，contracts/rest-api.md）。 */
    public KbDocumentRecord addDocument(String kb, Path sourceFile) {
        require(kb);
        String filename = sourceFile.getFileName().toString();
        validateFilename(filename);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(sourceFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件不存在或不可读: " + sourceFile);
        }
        return storeDocument(kb, filename, bytes);
    }

    /** 直传内容（REST 方式二）。 */
    public KbDocumentRecord addDocumentContent(String kb, String filename, String content) {
        require(kb);
        validateFilename(filename);
        return storeDocument(kb, filename, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public KbIngestService.IngestSummary ingest(String kb) {
        require(kb);
        return ingestService.ingest(kb);
    }

    public Path kbDir(String name) {
        return workspaceRoot.resolve("kb").resolve(name);
    }

    private KbDocumentRecord storeDocument(String kb, String filename, byte[] bytes) {
        String docPath = "docs/" + filename;
        if (store.findDocument(kb, docPath).isPresent()) {
            throw new KbConflictException(KbConflictException.KB_CONFLICT, "文档已存在: " + docPath);
        }
        Path target = docsDir(kb).resolve(filename);
        try {
            Files.createDirectories(docsDir(kb));
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("写入文档失败: " + target, e);
        }
        String hash = KbIngestService.sha256(target);
        return store.upsertDocument(kb, docPath, bytes.length, hash);
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException("非法文件名: " + filename);
        }
        String ext = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文档格式: ." + ext + "（仅 .md/.markdown/.txt）");
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Path docsDir(String name) {
        return kbDir(name).resolve("docs");
    }

    // ---- overview ----

    /** 结构总览（US5）：文档清单 + 标题路径大纲；库不存在时抛 KbNotFoundException。 */
    public List<KbOverviewEntry> overview(String kb) {
        require(kb);
        return store.overview(kb);
    }

    private static void deleteRecursive(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup; index rows are already gone
                }
            });
        } catch (IOException ignored) {
            // ditto
        }
    }
}
