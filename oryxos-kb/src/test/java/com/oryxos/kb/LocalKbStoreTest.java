package com.oryxos.kb;

import com.oryxos.storage.KbChunkRepository;
import com.oryxos.storage.KbDocumentRepository;
import com.oryxos.storage.KbFtsIndex;
import com.oryxos.storage.KbRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalKbStoreTest {

    private static final Path DB = tempDb();

    private static Path tempDb() {
        try {
            Path p = Files.createTempFile("oryxos-kb-store-test-", ".db");
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
                    // create-drop：测试目标是仓储行为而非 DDL。生产建表已改走
                    // schema.sql + ddl-auto=none（update 对 SQLite 已有表的抽取
                    // 有 空 COLUMN_DEF → NoSuchElementException 的兼容性坑）
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

    private static LocalKbStore newStore(ConfigurableApplicationContext ctx) {
        KbFtsIndex fts = new KbFtsIndex(new JdbcTemplate(ctx.getBean(DataSource.class)));
        return new LocalKbStore(
                ctx.getBean(KbRepository.class),
                ctx.getBean(KbDocumentRepository.class),
                ctx.getBean(KbChunkRepository.class),
                fts);
    }

    private static KbChunkData chunk(int ordinal, String heading, String content, String embeddingJson) {
        return new KbChunkData(ordinal, heading, content, embeddingJson);
    }

    @Test
    void createFindAndDuplicateRejected() {
        RUNNER.run(ctx -> {
            LocalKbStore store = newStore(ctx);
            KbRecord created = store.createKb("t1-basic", "基础库");
            assertEquals("t1-basic", created.name());
            assertTrue(store.findKb("t1-basic").isPresent());

            KbConflictException e = assertThrows(KbConflictException.class,
                    () -> store.createKb("t1-basic", "再次创建"));
            assertEquals(KbConflictException.KB_CONFLICT, e.getCode());
        });
    }

    @Test
    void documentUpsertResetsToPending() {
        RUNNER.run(ctx -> {
            LocalKbStore store = newStore(ctx);
            store.createKb("t2-docs", null);
            KbDocumentRecord d1 = store.upsertDocument("t2-docs", "docs/a.md", 100, "hash-1");
            assertEquals(KbDocumentStatus.PENDING, d1.status());

            store.markDocument(d1.id(), KbDocumentStatus.READY, null, 3);
            KbDocumentRecord ready = store.findDocument("t2-docs", "docs/a.md").orElseThrow();
            assertEquals(KbDocumentStatus.READY, ready.status());
            assertEquals(3, ready.chunkCount());
            assertNotNull(ready.ingestedAt());

            // 同路径重复 add → 重置 PENDING，错误与计数清空
            KbDocumentRecord d2 = store.upsertDocument("t2-docs", "docs/a.md", 120, "hash-2");
            assertEquals(KbDocumentStatus.PENDING, d2.status());
            assertEquals("hash-2", d2.contentHash());
            assertNull(d2.chunkCount());
            assertNull(d2.ingestedAt());
        });
    }

    @Test
    void replaceChunksRoundtripAndFts() {
        RUNNER.run(ctx -> {
            LocalKbStore store = newStore(ctx);
            store.createKb("t3-chunks", null);
            KbDocumentRecord doc = store.upsertDocument("t3-chunks", "docs/a.md", 10, "h");

            store.replaceChunks(doc.id(), "t3-chunks", List.of(
                    chunk(0, "指南", "默认端口是 8080", "[0.1,0.2]"),
                    chunk(1, "指南 > 高级", "支持增量摄取", "[0.3,0.4]")));

            List<KbChunkData> chunks = store.chunksOf(doc.id());
            assertEquals(2, chunks.size());
            assertEquals("默认端口是 8080", chunks.get(0).content());
            assertEquals("[0.3,0.4]", chunks.get(1).embeddingJson());

            assertFalse(store.ftsIndex().search("默认端口", "t3-chunks", 5).isEmpty());

            // 重放替换 → 旧分段与 FTS 行被整体替换，无重复命中
            store.replaceChunks(doc.id(), "t3-chunks", List.of(chunk(0, "指南", "默认端口是 8080", "[0.1,0.2]")));
            assertEquals(1, store.chunksOf(doc.id()).size());
            assertEquals(1, store.ftsIndex().search("默认端口", "t3-chunks", 20).size());
        });
    }

    @Test
    void removeDocumentDropsChunksAndFts() {
        RUNNER.run(ctx -> {
            LocalKbStore store = newStore(ctx);
            store.createKb("t4-remove", null);
            KbDocumentRecord doc = store.upsertDocument("t4-remove", "docs/b.md", 10, "h");
            store.replaceChunks(doc.id(), "t4-remove", List.of(chunk(0, null, "白名单配置", "[0.1]")));
            assertFalse(store.ftsIndex().search("白名单", "t4-remove", 5).isEmpty());

            store.removeDocument("t4-remove", "docs/b.md");
            assertTrue(store.findDocument("t4-remove", "docs/b.md").isEmpty());
            assertTrue(store.chunksOf(doc.id()).isEmpty());
            assertTrue(store.ftsIndex().search("白名单", "t4-remove", 5).isEmpty());
        });
    }

    @Test
    void deleteKbCascadesEverything() {
        RUNNER.run(ctx -> {
            LocalKbStore store = newStore(ctx);
            store.createKb("t5-cascade", null);
            KbDocumentRecord doc = store.upsertDocument("t5-cascade", "docs/c.md", 10, "h");
            store.replaceChunks(doc.id(), "t5-cascade", List.of(chunk(0, null, "级联删除验证", "[0.1]")));
            store.saveEmbeddingIdentity("t5-cascade", "text-embedding-v3", 1024);

            store.deleteKb("t5-cascade");
            assertTrue(store.findKb("t5-cascade").isEmpty());
            assertTrue(store.listDocuments("t5-cascade").isEmpty());
            assertTrue(store.chunksOf(doc.id()).isEmpty());
            assertTrue(store.ftsIndex().search("级联删除", "t5-cascade", 5).isEmpty());
        });
    }

    @Test
    void embeddingIdentityRoundtrip() {
        RUNNER.run(ctx -> {
            LocalKbStore store = newStore(ctx);
            store.createKb("t6-identity", null);
            assertTrue(store.embeddingIdentity("t6-identity").isEmpty());

            store.saveEmbeddingIdentity("t6-identity", "text-embedding-v3", 1024);
            KbIdentity identity = store.embeddingIdentity("t6-identity").orElseThrow();
            assertEquals("text-embedding-v3", identity.model());
            assertEquals(1024, identity.dimensions());
        });
    }

    @Test
    void overviewAggregatesHeadings() {
        RUNNER.run(ctx -> {
            LocalKbStore store = newStore(ctx);
            store.createKb("t7-overview", null);
            KbDocumentRecord doc = store.upsertDocument("t7-overview", "docs/d.md", 10, "h");
            store.replaceChunks(doc.id(), "t7-overview", List.of(
                    chunk(0, "部署指南", "a", "[0.1]"),
                    chunk(1, "部署指南 > Windows", "b", "[0.2]"),
                    chunk(2, "部署指南", "c", "[0.3]")));

            List<KbOverviewEntry> entries = store.overview("t7-overview");
            assertEquals(1, entries.size());
            assertEquals("docs/d.md", entries.get(0).docPath());
            assertEquals(List.of("部署指南", "部署指南 > Windows"), entries.get(0).headings());
        });
    }
}
