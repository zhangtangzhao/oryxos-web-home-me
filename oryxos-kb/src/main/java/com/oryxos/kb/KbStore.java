package com.oryxos.kb;

import java.util.List;
import java.util.Optional;

/**
 * 知识库存储端口（宪法 IX：接口先行）。实现负责分段行与 FTS 派生索引的
 * 一致维护，且所有写路径必须经 SqliteWriteGate（宪法 V / SC-006）。
 */
public interface KbStore {

    // ---- knowledge bases ----

    /** @throws KbConflictException KB_CONFLICT 重名 */
    KbRecord createKb(String name, String description);

    Optional<KbRecord> findKb(String name);

    List<KbRecord> listKbs();

    /** 级联删除：documents + chunks + FTS 行。 */
    void deleteKb(String name);

    // ---- documents ----

    /** 新建或重置为 PENDING（同 doc_path 覆盖指纹与大小）。 */
    KbDocumentRecord upsertDocument(String kb, String docPath, long sizeBytes, String contentHash);

    Optional<KbDocumentRecord> findDocument(String kb, String docPath);

    List<KbDocumentRecord> listDocuments(String kb);

    /** 状态迁移：READY 落 ingested_at；PENDING/FAILED 清 ingested_at（重试语义）。 */
    void markDocument(long documentId, KbDocumentStatus status, String errorMessage, Integer chunkCount);

    /** 删除文档及其全部分段与 FTS 行（增量摄取的移除路径，FR-008）。 */
    void removeDocument(String kb, String docPath);

    // ---- chunks ----

    /** 整文档替换分段（含嵌入 JSON）并同步重建该文档的 FTS 行。 */
    void replaceChunks(long documentId, String kb, List<KbChunkData> chunks);

    List<KbChunkData> chunksOf(long documentId);

    // ---- embedding identity (FR-015) ----

    Optional<KbIdentity> embeddingIdentity(String kb);

    void saveEmbeddingIdentity(String kb, String model, int dimensions);

    // ---- overview (US5) ----

    List<KbOverviewEntry> overview(String kb);

    // ---- search reads (US2) ----

    /** 关键词路命中（score 正相关；degraded=true = FTS 不可用走了 LIKE 降级）。 */
    record KeywordHit(long chunkId, double score, boolean degraded) {
    }

    /** 语义路检索数据：READY 文档的全量分段（引用元数据 + 嵌入向量）。 */
    record EmbeddedChunk(long chunkId, String docPath, int ordinal,
                         String headingPath, String content, float[] embedding) {
    }

    /** chunkId → 引用定位（关键词路命中回表）。 */
    record ChunkRef(long chunkId, String kb, String docPath, int ordinal,
                    String headingPath, String content) {
    }

    /** kb 范围内关键词检索（FTS BM25，引擎不可用自动 LIKE 降级），top-N。 */
    List<KeywordHit> searchKeywords(String kb, String query, int limit);

    /** kb 范围内全部分段向量（仅 READY 文档）。 */
    List<EmbeddedChunk> chunkVectors(String kb);

    Optional<ChunkRef> chunkById(long chunkId);
}
