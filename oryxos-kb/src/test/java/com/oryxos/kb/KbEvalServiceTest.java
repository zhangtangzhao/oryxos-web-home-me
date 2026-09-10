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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KbEvalService（US6）：真实库 + 固定向量假嵌入，验证 hit@k 记账、无效样例
 * 不计分、未命中与错误分支（contracts/cli.md §5）。仓库操作一律在
 * RUNNER.run 生命周期内完成（JPA 基础设施随上下文关闭）。
 */
class KbEvalServiceTest {

    private static final Path DB = tempDb();

    private static Path tempDb() {
        try {
            Path p = Files.createTempFile("oryxos-kb-eval-test-", ".db");
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

    /** 固定向量假嵌入客户端：同文本同向量。 */
    static class FakeEmbedding implements EmbeddingClient {

        @Override
        public List<float[]> embed(List<String> inputs) {
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
            return "fake-model";
        }

        @Override
        public int dimensions() {
            return 8;
        }
    }

    @TempDir
    Path ws;

    /** 建库 + 两篇文档（alpha/beta 互不共享 token）+ 摄取 + eval 服务。 */
    private KbEvalService seed(KbStore store, String kb) throws IOException {
        Path dir = ws.resolve("kb").resolve(kb).resolve("docs");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("a.md"), "# Alpha 文档\n\nremoval-alpha 只在这里出现。");
        Files.writeString(dir.resolve("b.md"), "# Beta 文档\n\nkeep-beta 保持可检索。");
        store.createKb(kb, null);
        new KbIngestService(store, new HeadingAwareChunker(500, 50), new FakeEmbedding(), ws)
                .ingest(kb);
        return new KbEvalService(store, new KbSearchService(store, new FakeEmbedding(),
                new NoopReranker(), new KbProperties()), ws);
    }

    private void writeEvalset(String kb, String yaml) throws IOException {
        Files.writeString(ws.resolve("kb").resolve(kb).resolve("evalset.yaml"), yaml);
    }

    @Test
    void hitsCountedWithRankAndFullRate() throws IOException {
        RUNNER.run(context -> {
            try {
                KbEvalService eval = seed(store(context), "t28-hit");
                writeEvalset("t28-hit", """
                        - {query: "removal-alpha", expected_path: "docs/a.md"}
                        - {query: "keep-beta", expected_path: "docs/b.md"}
                        """);
                KbEvalService.EvalReport r = eval.eval("t28-hit", 5);
                assertEquals(2, r.total());
                assertEquals(2, r.scored());
                assertEquals(2, r.hits());
                assertEquals(100.0, r.hitRate());
                assertEquals(0, r.zeroResults());
                assertTrue(r.avgDurationMs() >= 0);
                assertTrue(r.cases().get(0).hit());
                assertEquals(1, r.cases().get(0).hitRank());
                assertTrue(r.cases().get(1).hit());
                assertFalse(r.cases().get(0).invalid());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void invalidExpectedPathExcludedFromScore() throws IOException {
        RUNNER.run(context -> {
            try {
                KbEvalService eval = seed(store(context), "t28-invalid");
                writeEvalset("t28-invalid", """
                        - {query: "removal-alpha", expected_path: "docs/a.md"}
                        - {query: "任何问题", expected_path: "docs/missing.md"}
                        """);
                KbEvalService.EvalReport r = eval.eval("t28-invalid", 5);
                assertEquals(2, r.total());
                assertEquals(1, r.scored());
                assertEquals(1, r.hits());
                assertEquals(100.0, r.hitRate());
                assertTrue(r.cases().get(1).invalid());
                assertFalse(r.cases().get(1).hit());
                assertEquals(0, r.cases().get(1).durationMs());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void missRecordedWhenExpectedDocAbsentFromResults() throws IOException {
        RUNNER.run(context -> {
            try {
                KbStore store = store(context);
                KbEvalService eval = seed(store, "t28-miss");
                // c.md 已登记但未摄取（无分段），永远进不了检索结果 → 确定性未命中
                store.upsertDocument("t28-miss", "docs/c.md", 10, "hash-c");
                writeEvalset("t28-miss", """
                        - {query: "removal-alpha", expected_path: "docs/c.md"}
                        - {query: "removal-alpha", expected_path: "docs/a.md"}
                        """);
                KbEvalService.EvalReport r = eval.eval("t28-miss", 5);
                assertEquals(2, r.scored());
                assertEquals(1, r.hits());
                assertEquals(50.0, r.hitRate());
                assertFalse(r.cases().get(0).hit());
                assertEquals(0, r.cases().get(0).hitRank());
                assertFalse(r.cases().get(0).zeroResult());
                assertFalse(r.cases().get(0).invalid());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void missingEvalsetFails() throws IOException {
        RUNNER.run(context -> {
            try {
                KbEvalService eval = seed(store(context), "t28-noeval");
                assertThrows(IllegalArgumentException.class, () -> eval.eval("t28-noeval", 5));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void unknownKbFails() {
        RUNNER.run(context -> {
            KbEvalService eval = new KbEvalService(store(context), null, ws);
            assertThrows(KbNotFoundException.class, () -> eval.eval("t28-absent", 5));
        });
    }

    @Test
    void renderShowsHitMissInvalidAndRate() throws IOException {
        RUNNER.run(context -> {
            try {
                KbStore store = store(context);
                KbEvalService eval = seed(store, "t28-render");
                store.upsertDocument("t28-render", "docs/c.md", 10, "hash-c");
                writeEvalset("t28-render", """
                        - {query: "removal-alpha", expected_path: "docs/a.md"}
                        - {query: "removal-alpha", expected_path: "docs/c.md"}
                        - {query: "任何问题", expected_path: "docs/missing.md"}
                        """);
                KbEvalService.EvalReport r = eval.eval("t28-render", 5);
                String out = KbEvalService.render(r);
                assertTrue(out.contains("kb=t28-render 样例 3 条（有效 2）"));
                assertTrue(out.contains("命中 1 → hit@5 = 50.0%"));
                assertTrue(out.contains("[命中 rank=1] removal-alpha → docs/a.md"));
                assertTrue(out.contains("[未命中] removal-alpha → docs/c.md"));
                assertTrue(out.contains("[无效] 任何问题 → docs/missing.md（库中不存在，不计分）"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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
}
