package com.oryxos.kb;

import com.oryxos.storage.KbChunkRepository;
import com.oryxos.storage.KbDocumentRepository;
import com.oryxos.storage.KbFtsIndex;
import com.oryxos.storage.KbRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbIngestServiceTest {

    private static final Path DB = tempDb();

    private static Path tempDb() {
        try {
            Path p = Files.createTempFile("oryxos-kb-ingest-test-", ".db");
            Files.delete(p);
            p.toFile().deleteOnExit();
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final ApplicationContextRunner RUNNER = new ApplicationContextRunner()
            .withPropertyValues(
                    "spring.datasource.url=jdbc:sqlite:" + DB.toAbsolutePath().toString().replace('\\', '/'),
                    "spring.datasource.driver-class-name=org.sqlite.JDBC",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect")
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class))
            .withUserConfiguration(TestPersistence.class);

    @Configuration
    @EnableJpaRepositories(basePackages = "com.oryxos.storage")
    @EntityScan(basePackages = "com.oryxos.storage")
    static class TestPersistence {
    }

    /** 固定向量假嵌入客户端：同文本同向量，可切换模型身份/故障模式。 */
    static class FakeEmbedding implements EmbeddingClient {
        String model = "fake-model";
        boolean notConfigured;
        boolean unavailable;

        FakeEmbedding(String model) {
            this.model = model;
        }

        @Override
        public List<float[]> embed(List<String> inputs) {
            if (notConfigured) {
                throw new EmbeddingNotConfiguredException("测试：未配置嵌入服务");
            }
            if (unavailable) {
                throw new EmbeddingUnavailableException("测试：嵌入服务不可达");
            }
            return inputs.stream().map(FakeEmbedding::vector).toList();
        }

        static float[] vector(String s) {
            float[] v = new float[8];
            for (int i = 0; i < s.length(); i++) {
                v[Math.floorMod(s.charAt(i), 8)] += 1;
            }
            float norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < v.length; i++) {
                v[i] = norm == 0 ? 0 : v[i] / norm;
            }
            return v;
        }

        @Override
        public String model() {
            return model;
        }

        @Override
        public int dimensions() {
            return 8;
        }
    }

    @TempDir
    Path ws;

    private final FakeEmbedding fake = new FakeEmbedding("fake-model");

    private Path writeDoc(String kb, String name, String content) throws IOException {
        Path dir = ws.resolve("kb").resolve(kb).resolve("docs");
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), content);
    }

    @Test
    void processesNewDocumentsAndRecordsIdentity() throws IOException {
        writeDoc("t10-process", "a.md", "# 事实\n\nOryxOS 默认端口是 8080。");
        writeDoc("t10-process", "b.txt", "纯文本内容说明。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-process", null);
            KbIngestService ingest = ingest(store);

            KbIngestService.IngestSummary s = ingest.ingest("t10-process");
            assertEquals(2, s.processed());
            assertEquals(0, s.skipped());
            assertEquals(0, s.removed());
            assertEquals(0, s.failed());
            assertTrue(s.durationMs() >= 0);

            for (KbDocumentRecord d : store.listDocuments("t10-process")) {
                assertEquals(KbDocumentStatus.READY, d.status());
                assertNotNull(d.chunkCount());
                assertTrue(d.chunkCount() > 0);
            }
            assertEquals(new KbIdentity("fake-model", 8),
                    store.embeddingIdentity("t10-process").orElseThrow());
        });
    }

    @Test
    void secondIngestSkipsUnchanged() throws IOException {
        writeDoc("t10-skip", "a.md", "# 事实\n\n内容一。");
        writeDoc("t10-skip", "b.md", "# 事实\n\n内容二。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-skip", null);
            KbIngestService ingest = ingest(store);

            assertEquals(2, ingest.ingest("t10-skip").processed());
            KbIngestService.IngestSummary s2 = ingest.ingest("t10-skip");
            assertEquals(0, s2.processed());
            assertEquals(2, s2.skipped());
        });
    }

    @Test
    void onlyChangedDocumentReprocessed() throws IOException {
        writeDoc("t10-change", "a.md", "# 事实\n\n原始内容。");
        writeDoc("t10-change", "b.md", "# 事实\n\n不变内容。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-change", null);
            KbIngestService ingest = ingest(store);
            ingest.ingest("t10-change");

            writeDoc("t10-change", "a.md", "# 事实\n\n原始内容。\n\n补充说明：支持 WAL 模式。");
            KbIngestService.IngestSummary s = ingest.ingest("t10-change");
            assertEquals(1, s.processed());
            assertEquals(1, s.skipped());
        });
    }

    @Test
    void modelIdentityMismatchRejected() throws IOException {
        writeDoc("t10-mismatch", "a.md", "# 事实\n\n内容。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-mismatch", null);
            KbIngestService ingest = ingest(store);
            ingest.ingest("t10-mismatch");

            fake.model = "fake-model-v2";
            KbConflictException e = assertThrows(KbConflictException.class,
                    () -> ingest.ingest("t10-mismatch"));
            assertEquals(KbConflictException.EMBEDDING_MISMATCH, e.getCode());
            assertTrue(e.getMessage().contains("fake-model"));
        });
    }

    @Test
    void notConfiguredPropagates() throws IOException {
        writeDoc("t10-notconf", "a.md", "# 事实\n\n内容。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-notconf", null);
            fake.notConfigured = true;
            KbIngestService ingest = ingest(store);
            assertThrows(EmbeddingNotConfiguredException.class, () -> ingest.ingest("t10-notconf"));
            assertEquals(KbDocumentStatus.PENDING,
                    store.findDocument("t10-notconf", "docs/a.md").orElseThrow().status());
        });
    }

    @Test
    void unavailableKeepsDocumentsPending() throws IOException {
        writeDoc("t10-unavail", "a.md", "# 事实\n\n内容。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-unavail", null);
            fake.unavailable = true;
            KbIngestService ingest = ingest(store);
            assertThrows(EmbeddingUnavailableException.class, () -> ingest.ingest("t10-unavail"));
            KbDocumentRecord doc = store.findDocument("t10-unavail", "docs/a.md").orElseThrow();
            assertEquals(KbDocumentStatus.PENDING, doc.status());
            assertNull(doc.ingestedAt());
        });
    }

    @Test
    void removedDocumentDropsChunksAndFtsRows() throws IOException {
        writeDoc("t10-remove", "gone.md", "# 移除目标\n\nremoval-token-alpha 只在这里出现。");
        writeDoc("t10-remove", "keep.md", "# 保留\n\nkeep-token-beta 保持可检索。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-remove", null);
            KbIngestService ingest = ingest(store);
            assertEquals(2, ingest.ingest("t10-remove").processed());

            // 移除前：两篇均可关键词命中
            assertEquals(1, store.searchKeywords("t10-remove", "alpha", 10).size());
            assertEquals(1, store.searchKeywords("t10-remove", "beta", 10).size());

            Files.delete(ws.resolve("kb").resolve("t10-remove").resolve("docs").resolve("gone.md"));
            KbIngestService.IngestSummary s = ingest.ingest("t10-remove");
            assertEquals(1, s.removed());
            assertEquals(0, s.processed());
            assertEquals(0, s.failed());

            // 文档行、分段行、FTS 行均清理（FR-008）；保留文档不受影响
            assertTrue(store.findDocument("t10-remove", "docs/gone.md").isEmpty());
            assertEquals(1, store.listDocuments("t10-remove").size());
            assertEquals(0, store.searchKeywords("t10-remove", "alpha", 10).size());
            assertEquals(1, store.searchKeywords("t10-remove", "beta", 10).size());
        });
    }

    @Test
    void failedDocumentRetriesOnNextIngest() throws IOException {
        writeDoc("t10-retry", "empty.md", "");
        writeDoc("t10-retry", "good.md", "# 内容\n\ngood-token 持续有效。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-retry", null);
            KbIngestService ingest = ingest(store);

            KbIngestService.IngestSummary s1 = ingest.ingest("t10-retry");
            assertEquals(1, s1.failed());
            assertEquals(KbDocumentStatus.FAILED,
                    store.findDocument("t10-retry", "docs/empty.md").orElseThrow().status());

            // 失败文档内容修复后（哈希变化）下次摄取自动重试成功
            writeDoc("t10-retry", "empty.md", "# 补写\n\nrepaired-token 修复后可检索。");
            KbIngestService.IngestSummary s2 = ingest.ingest("t10-retry");
            assertEquals(1, s2.processed());
            assertEquals(0, s2.failed());
            assertEquals(KbDocumentStatus.READY,
                    store.findDocument("t10-retry", "docs/empty.md").orElseThrow().status());
            assertEquals(1, store.searchKeywords("t10-retry", "repaired", 10).size());
        });
    }

    @Test
    void pendingDocumentRetriesAfterEmbeddingRecovers() throws IOException {
        writeDoc("t10-pending", "a.md", "# 事实\n\npending-token 内容。");
        RUNNER.run(context -> {
            KbStore store = store(context);
            store.createKb("t10-pending", null);
            fake.unavailable = true;
            KbIngestService ingest = ingest(store);
            assertThrows(EmbeddingUnavailableException.class, () -> ingest.ingest("t10-pending"));

            // 嵌入恢复后重跑：PENDING 文档重试成功并落 FTS/向量
            fake.unavailable = false;
            KbIngestService.IngestSummary s = ingest.ingest("t10-pending");
            assertEquals(1, s.processed());
            assertEquals(KbDocumentStatus.READY,
                    store.findDocument("t10-pending", "docs/a.md").orElseThrow().status());
            assertEquals(1, store.searchKeywords("t10-pending", "pending", 10).size());
        });
    }

    private KbStore store(org.springframework.context.ConfigurableApplicationContext context) {
        KbFtsIndex fts = new KbFtsIndex(new JdbcTemplate(context.getBean(DataSource.class)));
        return new LocalKbStore(
                context.getBean(KbRepository.class),
                context.getBean(KbDocumentRepository.class),
                context.getBean(KbChunkRepository.class),
                fts);
    }

    private KbIngestService ingest(KbStore store) {
        return new KbIngestService(store, new HeadingAwareChunker(500, 50), fake, ws);
    }
}
