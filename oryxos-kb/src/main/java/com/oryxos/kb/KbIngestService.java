package com.oryxos.kb;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 增量摄取（US1/US3，research D8）：docs/ 目录为事实源——
 * 指纹（sha256）未变且已 ready 的跳过；新建/变更的重嵌入；磁盘上消失的移除。
 * 嵌入身份与库记录不符时拒绝（FR-015）；嵌入未配置/不可达时中止且文档保留
 * pending 可重试（contracts/cli.md）。
 */
public class KbIngestService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "markdown", "txt");

    private final KbStore store;
    private final KbChunker chunker;
    private final EmbeddingClient embedding;
    private final Path workspaceRoot;

    public KbIngestService(KbStore store, KbChunker chunker, EmbeddingClient embedding, Path workspaceRoot) {
        this.store = store;
        this.chunker = chunker;
        this.embedding = embedding;
        this.workspaceRoot = workspaceRoot;
    }

    public record DocumentOutcome(String docPath, String status, int chunkCount, String error) {
    }

    public record IngestSummary(int processed, int skipped, int removed, int failed,
                                long durationMs, List<DocumentOutcome> documents) {
    }

    public IngestSummary ingest(String kb) {
        long start = System.currentTimeMillis();
        if (store.findKb(kb).isEmpty()) {
            throw new KbNotFoundException(kb);
        }
        checkEmbeddingIdentity(kb);

        Path docsDir = docsDir(kb);
        List<DocumentOutcome> outcomes = new ArrayList<>();
        int processed = 0;
        int skipped = 0;
        int removed = 0;
        int failed = 0;

        Set<String> diskPaths = new HashSet<>();
        if (Files.isDirectory(docsDir)) {
            try (Stream<Path> files = Files.list(docsDir)) {
                files.filter(Files::isRegularFile)
                        .filter(f -> SUPPORTED_EXTENSIONS.contains(extension(f)))
                        .sorted()
                        .forEach(f -> diskPaths.add(docPathOf(f)));
            } catch (IOException e) {
                throw new UncheckedIOException("扫描文档目录失败: " + docsDir, e);
            }
        }

        // 磁盘上消失的文档 → 移除（分段 + FTS + 行，FR-008）
        for (KbDocumentRecord doc : store.listDocuments(kb)) {
            if (!diskPaths.contains(doc.docPath())) {
                store.removeDocument(kb, doc.docPath());
                removed++;
                outcomes.add(new DocumentOutcome(doc.docPath(), "removed", 0, null));
            }
        }

        boolean identitySaved = store.embeddingIdentity(kb).isPresent();

        for (String docPath : diskPaths.stream().sorted().toList()) {
            Path file = docsDir.resolve(Path.of(docPath).getFileName().toString());
            String contentHash = sha256(file);
            KbDocumentRecord existing = store.findDocument(kb, docPath).orElse(null);
            if (existing != null && contentHash.equals(existing.contentHash())
                    && existing.status() == KbDocumentStatus.READY) {
                skipped++;
                outcomes.add(new DocumentOutcome(docPath, "skipped", existing.chunkCount() == null ? 0 : existing.chunkCount(), null));
                continue;
            }
            String content = readString(file);
            KbDocumentRecord doc = store.upsertDocument(kb, docPath,
                    safeSize(file), contentHash);
            try {
                List<KbChunk> chunks = chunker.chunk(content);
                if (chunks.isEmpty()) {
                    store.markDocument(doc.id(), KbDocumentStatus.FAILED, "文档无有效内容", 0);
                    failed++;
                    outcomes.add(new DocumentOutcome(docPath, "failed", 0, "文档无有效内容"));
                    continue;
                }
                List<String> texts = chunks.stream().map(KbChunk::content).toList();
                List<float[]> vectors = embedding.embed(texts);
                List<KbChunkData> data = new ArrayList<>(chunks.size());
                for (int i = 0; i < chunks.size(); i++) {
                    data.add(new KbChunkData(i, chunks.get(i).headingPath(),
                            chunks.get(i).content(), MAPPER.writeValueAsString(vectors.get(i))));
                }
                store.replaceChunks(doc.id(), kb, data);
                store.markDocument(doc.id(), KbDocumentStatus.READY, null, chunks.size());
                processed++;
                outcomes.add(new DocumentOutcome(docPath, "ready", chunks.size(), null));
                if (!identitySaved) {
                    store.saveEmbeddingIdentity(kb, embedding.model(), embedding.dimensions());
                    identitySaved = true;
                }
            } catch (EmbeddingNotConfiguredException | EmbeddingUnavailableException e) {
                // 文档留在 PENDING 可重试；异常上抛由 CLI/REST 层按契约呈现
                throw e;
            } catch (Exception e) {
                store.markDocument(doc.id(), KbDocumentStatus.FAILED, e.getMessage(), 0);
                failed++;
                outcomes.add(new DocumentOutcome(docPath, "failed", 0, e.getMessage()));
            }
        }

        return new IngestSummary(processed, skipped, removed, failed,
                System.currentTimeMillis() - start, outcomes);
    }

    /** FR-015：库已记录的嵌入身份与当前配置不一致 → 拒绝静默重嵌。 */
    private void checkEmbeddingIdentity(String kb) {
        store.embeddingIdentity(kb).ifPresent(identity -> {
            if (!identity.model().equals(embedding.model()) || identity.dimensions() != embedding.dimensions()) {
                throw new KbConflictException(KbConflictException.EMBEDDING_MISMATCH,
                        "知识库检索不可用：嵌入模型已变更（" + identity.model() + " → " + embedding.model()
                                + "），请恢复配置或重建知识库");
            }
        });
    }

    private Path docsDir(String kb) {
        return workspaceRoot.resolve("kb").resolve(kb).resolve("docs");
    }

    private static String docPathOf(Path file) {
        return "docs/" + file.getFileName();
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文档失败: " + file, e);
        }
    }

    private static long safeSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    digest.update(buf, 0, n);
                }
            }
            return hex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("计算文档指纹失败: " + file, e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
