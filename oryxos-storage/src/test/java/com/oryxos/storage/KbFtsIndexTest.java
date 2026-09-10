package com.oryxos.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbFtsIndexTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private KbFtsIndex index;

    @BeforeEach
    void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("fts-test.db").toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        // 模拟 kb_chunks 表（LIKE 降级路与 rebuild 的数据源）
        jdbc.execute("CREATE TABLE kb_chunks (id INTEGER PRIMARY KEY, kb_name TEXT, content TEXT)");
        index = new KbFtsIndex(jdbc);
    }

    private void insertChunk(long id, String kb, String content) {
        jdbc.update("INSERT INTO kb_chunks(id, kb_name, content) VALUES (?, ?, ?)", id, kb, content);
    }

    private List<KbFtsIndex.FtsRow> rows(long baseId, String kb, String... contents) {
        long[] next = {baseId};
        return java.util.Arrays.stream(contents)
                .map(c -> new KbFtsIndex.FtsRow(next[0]++, kb, BigramTokenizer.tokenize(c)))
                .toList();
    }

    @Test
    void writeThenBm25SearchHits() {
        insertChunk(1, "product-docs", "OryxOS 默认端口是 8080。");
        insertChunk(2, "product-docs", "部署指南：Windows 下使用 zip 包。");
        index.replaceByDocument(List.of(), rows(1, "product-docs",
                "OryxOS 默认端口是 8080。", "部署指南：Windows 下使用 zip 包。"));

        List<KbFtsIndex.FtsHit> hits = index.search("默认端口", "product-docs", 5);
        assertFalse(hits.isEmpty());
        assertFalse(hits.get(0).degraded());
        assertEquals(1L, hits.get(0).chunkId());
    }

    @Test
    void kbScopeFiltersResults() {
        index.replaceByDocument(List.of(), rows(10, "kb-a", "白名单配置说明"));
        index.replaceByDocument(List.of(), rows(20, "kb-b", "白名单配置说明"));

        List<KbFtsIndex.FtsHit> hits = index.search("白名单", "kb-a", 5);
        assertFalse(hits.isEmpty());
        hits.forEach(h -> assertEquals(10L, h.chunkId()));
    }

    @Test
    void deleteInvalidatesSearch() {
        index.replaceByDocument(List.of(), rows(1, "kb", "定时任务触发器配置"));
        assertFalse(index.search("定时任务", "kb", 5).isEmpty());

        index.deleteByChunkIds(List.of(1L));
        assertTrue(index.search("定时任务", "kb", 5).isEmpty());
    }

    @Test
    void replaceDropsOldRows() {
        index.replaceByDocument(List.of(), rows(1, "kb", "旧的端口内容"));
        assertFalse(index.search("旧的端口", "kb", 5).isEmpty());

        index.replaceByDocument(List.of(1L), rows(2, "kb", "全新的内存内容"));
        assertTrue(index.search("旧的端口", "kb", 5).isEmpty());
        assertFalse(index.search("全新的内存", "kb", 5).isEmpty());
    }

    @Test
    void rebuildRestoresIndexFromChunks() {
        insertChunk(1, "kb", "评估集命中判定规则");
        jdbc.update("DELETE FROM kb_chunks_fts"); // 模拟索引丢失
        assertTrue(index.search("评估集", "kb", 5).isEmpty());

        assertEquals(1, index.rebuild());
        List<KbFtsIndex.FtsHit> hits = index.search("评估集", "kb", 5);
        assertFalse(hits.isEmpty());
        assertEquals(1L, hits.get(0).chunkId());
    }

    @Test
    void likeFallbackScoresAndMarksDegraded() {
        insertChunk(1, "kb", "默认端口是 8080，支持环境变量覆盖。");
        insertChunk(2, "kb", "完全无关的内容。");
        List<KbFtsIndex.FtsHit> hits = index.likeSearch("默认端口", "kb", 5);
        assertEquals(1, hits.size());
        assertEquals(1L, hits.get(0).chunkId());
        assertTrue(hits.get(0).degraded());
    }
}
