package com.oryxos.storage;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * kb_chunks 的 FTS5 派生全文索引（research D3）：chunk 行的写入方
 * （LocalKbStore）负责同步维护本索引；检索关键词路由 BM25 排序。
 * - chunk_id/kb_name 为 UNINDEXED 列，检索按 kb_name 收窄，不 JOIN 实体表；
 * - 全部写操作在 SqliteWriteGate 内（宪法 V/SC-006）；
 * - FTS5 不可用（旧引擎/建表失败）时自动降级为 LIKE 匹配，结果标注 degraded。
 */
public class KbFtsIndex {

    private final JdbcTemplate jdbc;
    private volatile boolean ftsAvailable = false;

    public KbFtsIndex(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        try {
            jdbc.execute("CREATE VIRTUAL TABLE IF NOT EXISTS kb_chunks_fts "
                    + "USING fts5(chunk_id UNINDEXED, kb_name UNINDEXED, tokens)");
            this.ftsAvailable = true;
        } catch (DataAccessException e) {
            this.ftsAvailable = false;
        }
    }

    /** true = 检索正走 LIKE 降级路。 */
    public boolean isDegraded() {
        return !ftsAvailable;
    }

    public record FtsRow(long chunkId, String kbName, String tokens) {
    }

    /** score 为正相关得分（-bm25，越大越相关）；degraded=true 表示 LIKE 降级结果。 */
    public record FtsHit(long chunkId, double score, boolean degraded) {
    }

    public void replaceByDocument(List<Long> oldChunkIds, List<FtsRow> newRows) {
        SqliteWriteGate.write(() -> {
            deleteByChunkIdsInternal(oldChunkIds);
            insert(newRows);
        });
    }

    public void deleteByChunkIds(List<Long> chunkIds) {
        SqliteWriteGate.write(() -> deleteByChunkIdsInternal(chunkIds));
    }

    private void deleteByChunkIdsInternal(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty() || !ftsAvailable) {
            return;
        }
        for (int from = 0; from < chunkIds.size(); from += 500) {
            List<Long> batch = chunkIds.subList(from, Math.min(from + 500, chunkIds.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            jdbc.update("DELETE FROM kb_chunks_fts WHERE chunk_id IN (" + placeholders + ")", batch.toArray());
        }
    }

    private void insert(List<FtsRow> rows) {
        if (rows == null || rows.isEmpty() || !ftsAvailable) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO kb_chunks_fts(chunk_id, kb_name, tokens) VALUES (?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        FtsRow row = rows.get(i);
                        ps.setLong(1, row.chunkId());
                        ps.setString(2, row.kbName());
                        ps.setString(3, row.tokens());
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
    }

    /**
     * 关键词检索：FTS5 MATCH（OR 组合保召回）+ BM25 排序，最佳在前；
     * FTS 不可用时走 LIKE 降级（Java 计分，degraded=true）。
     */
    public List<FtsHit> search(String query, String kbName, int limit) {
        String tokens = BigramTokenizer.tokenize(query);
        if (tokens.isBlank()) {
            return List.of();
        }
        if (!ftsAvailable) {
            return likeSearch(query, kbName, limit);
        }
        String match = String.join(" OR ", tokens.split("\\s+"));
        try {
            return jdbc.query(
                    "SELECT chunk_id, bm25(kb_chunks_fts) AS score FROM kb_chunks_fts "
                            + "WHERE kb_chunks_fts MATCH ? AND kb_name = ? ORDER BY score LIMIT ?",
                    (rs, n) -> new FtsHit(rs.getLong("chunk_id"), -rs.getDouble("score"), false),
                    match, kbName, limit);
        } catch (DataAccessException e) {
            this.ftsAvailable = false;
            return likeSearch(query, kbName, limit);
        }
    }

    /** LIKE 降级路：拉取库内分段按查询 token 覆盖数计分（本地小语量级可接受）。 */
    List<FtsHit> likeSearch(String query, String kbName, int limit) {
        Set<String> queryTokens = new HashSet<>(List.of(BigramTokenizer.tokenize(query).split("\\s+")));
        queryTokens.remove("");
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        List<FtsHit> hits = new ArrayList<>();
        List<KbChunkEntity> candidates = jdbc.query(
                "SELECT id, content FROM kb_chunks WHERE kb_name = ?",
                (rs, n) -> {
                    KbChunkEntity c = new KbChunkEntity();
                    c.setId(rs.getLong("id"));
                    c.setContent(rs.getString("content"));
                    return c;
                },
                kbName);
        for (KbChunkEntity c : candidates) {
            String tokens = BigramTokenizer.tokenize(c.getContent());
            if (tokens.isBlank()) {
                continue;
            }
            double score = 0;
            for (String t : queryTokens) {
                if (tokens.contains(t)) {
                    score += 1;
                }
            }
            if (score > 0) {
                hits.add(new FtsHit(c.getId(), score, true));
            }
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    /** 从 kb_chunks 全量重建索引（含 tokens 派生），返回重建行数。 */
    public long rebuild() {
        return SqliteWriteGate.write(() -> {
            if (ftsAvailable) {
                jdbc.update("DELETE FROM kb_chunks_fts");
            }
            List<FtsRow> rows = new ArrayList<>();
            jdbc.query("SELECT id, kb_name, content FROM kb_chunks", rs -> {
                rows.add(new FtsRow(rs.getLong("id"), rs.getString("kb_name"),
                        BigramTokenizer.tokenize(rs.getString("content"))));
            });
            insert(rows);
            return (long) rows.size();
        });
    }
}
