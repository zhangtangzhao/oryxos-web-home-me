package com.oryxos.kb;

import com.oryxos.storage.BigramTokenizer;
import com.oryxos.storage.KbChunkEntity;
import com.oryxos.storage.KbChunkRepository;
import com.oryxos.storage.KbDocumentEntity;
import com.oryxos.storage.KbDocumentRepository;
import com.oryxos.storage.KbEntity;
import com.oryxos.storage.KbFtsIndex;
import com.oryxos.storage.KbRepository;
import com.oryxos.storage.SqliteWriteGate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * KbStore 的 SQLite 本地实现（research D3/D7）：三张 JPA 表 + FTS5 派生索引，
 * 全部写操作经 SqliteWriteGate 串行（分段行与 FTS 行同闸门内一致替换）。
 */
public class LocalKbStore implements KbStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KbRepository kbRepo;
    private final KbDocumentRepository docRepo;
    private final KbChunkRepository chunkRepo;
    private final KbFtsIndex fts;

    public LocalKbStore(KbRepository kbRepo, KbDocumentRepository docRepo,
                        KbChunkRepository chunkRepo, KbFtsIndex fts) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.fts = fts;
    }

    /** 测试/装配便利：从既有 JdbcTemplate 派生 FTS 索引。 */
    public LocalKbStore(KbRepository kbRepo, KbDocumentRepository docRepo,
                        KbChunkRepository chunkRepo, JdbcTemplate jdbc) {
        this(kbRepo, docRepo, chunkRepo, new KbFtsIndex(jdbc));
    }

    public KbFtsIndex ftsIndex() {
        return fts;
    }

    // ---- knowledge bases ----

    @Override
    public KbRecord createKb(String name, String description) {
        return SqliteWriteGate.write(() -> {
            if (kbRepo.existsById(name)) {
                throw new KbConflictException(KbConflictException.KB_CONFLICT, "知识库已存在: " + name);
            }
            Instant now = Instant.now();
            KbEntity e = new KbEntity();
            e.setName(name);
            e.setDescription(description);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            kbRepo.save(e);
            return toRecord(e);
        });
    }

    @Override
    public Optional<KbRecord> findKb(String name) {
        return kbRepo.findById(name).map(LocalKbStore::toRecord);
    }

    @Override
    public List<KbRecord> listKbs() {
        return kbRepo.findAll().stream().map(LocalKbStore::toRecord)
                .sorted(java.util.Comparator.comparing(KbRecord::name)).toList();
    }

    @Override
    public void deleteKb(String name) {
        SqliteWriteGate.write(() -> {
            List<KbDocumentEntity> docs = docRepo.findByKbNameOrderByDocPath(name);
            for (KbDocumentEntity doc : docs) {
                deleteChunksOf(doc);
            }
            docRepo.deleteAll(docs);
            kbRepo.findById(name).ifPresent(kbRepo::delete);
        });
    }

    // ---- documents ----

    @Override
    public KbDocumentRecord upsertDocument(String kb, String docPath, long sizeBytes, String contentHash) {
        return SqliteWriteGate.write(() -> {
            KbDocumentEntity doc = docRepo.findByKbNameAndDocPath(kb, docPath).orElseGet(KbDocumentEntity::new);
            doc.setKbName(kb);
            doc.setDocPath(docPath);
            doc.setContentHash(contentHash);
            doc.setSizeBytes(sizeBytes);
            doc.setStatus(KbDocumentEntity.DocumentStatus.PENDING);
            doc.setErrorMessage(null);
            doc.setChunkCount(null);
            doc.setIngestedAt(null);
            return toRecord(docRepo.save(doc));
        });
    }

    @Override
    public Optional<KbDocumentRecord> findDocument(String kb, String docPath) {
        return docRepo.findByKbNameAndDocPath(kb, docPath).map(LocalKbStore::toRecord);
    }

    @Override
    public List<KbDocumentRecord> listDocuments(String kb) {
        return docRepo.findByKbNameOrderByDocPath(kb).stream().map(LocalKbStore::toRecord).toList();
    }

    @Override
    public void markDocument(long documentId, KbDocumentStatus status, String errorMessage, Integer chunkCount) {
        SqliteWriteGate.write(() -> {
            KbDocumentEntity doc = docRepo.findById(documentId).orElse(null);
            if (doc == null) {
                return;
            }
            doc.setStatus(KbDocumentEntity.DocumentStatus.valueOf(status.name()));
            doc.setErrorMessage(errorMessage);
            doc.setChunkCount(chunkCount);
            doc.setIngestedAt(status == KbDocumentStatus.READY ? Instant.now() : null);
            docRepo.save(doc);
        });
    }

    @Override
    public void removeDocument(String kb, String docPath) {
        SqliteWriteGate.write(() -> {
            KbDocumentEntity doc = docRepo.findByKbNameAndDocPath(kb, docPath).orElse(null);
            if (doc == null) {
                return;
            }
            deleteChunksOf(doc);
            docRepo.delete(doc);
        });
    }

    // ---- chunks ----

    @Override
    public void replaceChunks(long documentId, String kb, List<KbChunkData> chunks) {
        SqliteWriteGate.write(() -> {
            List<KbChunkEntity> old = chunkRepo.findByDocumentIdOrderByChunkOrdinal(documentId);
            List<Long> oldIds = old.stream().map(KbChunkEntity::getId).toList();
            chunkRepo.deleteAll(old);

            List<KbChunkEntity> created = new ArrayList<>(chunks.size());
            for (KbChunkData c : chunks) {
                KbChunkEntity e = new KbChunkEntity();
                e.setDocumentId(documentId);
                e.setKbName(kb);
                e.setChunkOrdinal(c.ordinal());
                e.setHeadingPath(c.headingPath());
                e.setContent(c.content());
                e.setEmbedding(c.embeddingJson());
                created.add(chunkRepo.save(e));
            }
            List<KbFtsIndex.FtsRow> rows = created.stream()
                    .map(e -> new KbFtsIndex.FtsRow(e.getId(), kb, BigramTokenizer.tokenize(e.getContent())))
                    .toList();
            fts.replaceByDocument(oldIds, rows);
        });
    }

    @Override
    public List<KbChunkData> chunksOf(long documentId) {
        return chunkRepo.findByDocumentIdOrderByChunkOrdinal(documentId).stream()
                .map(e -> new KbChunkData(e.getChunkOrdinal(), e.getHeadingPath(), e.getContent(), e.getEmbedding()))
                .toList();
    }

    private void deleteChunksOf(KbDocumentEntity doc) {
        List<KbChunkEntity> chunks = chunkRepo.findByDocumentIdOrderByChunkOrdinal(doc.getId());
        List<Long> ids = chunks.stream().map(KbChunkEntity::getId).toList();
        fts.deleteByChunkIds(ids);
        chunkRepo.deleteAll(chunks);
    }

    // ---- embedding identity ----

    @Override
    public Optional<KbIdentity> embeddingIdentity(String kb) {
        return kbRepo.findById(kb)
                .filter(e -> e.getEmbeddingModel() != null && e.getEmbeddingDimensions() != null)
                .map(e -> new KbIdentity(e.getEmbeddingModel(), e.getEmbeddingDimensions()));
    }

    @Override
    public void saveEmbeddingIdentity(String kb, String model, int dimensions) {
        SqliteWriteGate.write(() -> {
            KbEntity e = kbRepo.findById(kb).orElse(null);
            if (e == null) {
                return;
            }
            e.setEmbeddingModel(model);
            e.setEmbeddingDimensions(dimensions);
            e.setUpdatedAt(Instant.now());
            kbRepo.save(e);
        });
    }

    // ---- overview ----

    @Override
    public List<KbOverviewEntry> overview(String kb) {
        List<KbOverviewEntry> entries = new ArrayList<>();
        for (KbDocumentRecord doc : listDocuments(kb)) {
            List<String> headings = new ArrayList<>();
            for (KbChunkData c : chunksOf(doc.id())) {
                String h = c.headingPath();
                if (h != null && !h.isBlank() && !headings.contains(h)) {
                    headings.add(h);
                }
            }
            entries.add(new KbOverviewEntry(doc.docPath(), headings));
        }
        return entries;
    }

    // ---- search reads ----

    @Override
    public List<KeywordHit> searchKeywords(String kb, String query, int limit) {
        return fts.search(query, kb, limit).stream()
                .map(h -> new KeywordHit(h.chunkId(), h.score(), h.degraded()))
                .toList();
    }

    @Override
    public List<EmbeddedChunk> chunkVectors(String kb) {
        Map<Long, String> docPaths = new LinkedHashMap<>();
        for (KbDocumentEntity doc : docRepo.findByKbNameOrderByDocPath(kb)) {
            if (doc.getStatus() == KbDocumentEntity.DocumentStatus.READY) {
                docPaths.put(doc.getId(), doc.getDocPath());
            }
        }
        List<EmbeddedChunk> out = new ArrayList<>();
        for (Long documentId : docPaths.keySet()) {
            for (KbChunkEntity c : chunkRepo.findByDocumentIdOrderByChunkOrdinal(documentId)) {
                float[] vector = parseVector(c.getEmbedding());
                if (vector == null) {
                    continue;
                }
                out.add(new EmbeddedChunk(c.getId(), docPaths.get(documentId), c.getChunkOrdinal(),
                        c.getHeadingPath(), c.getContent(), vector));
            }
        }
        return out;
    }

    @Override
    public Optional<ChunkRef> chunkById(long chunkId) {
        return chunkRepo.findById(chunkId).flatMap(c -> docRepo.findById(c.getDocumentId())
                .map(doc -> new ChunkRef(c.getId(), c.getKbName(), doc.getDocPath(),
                        c.getChunkOrdinal(), c.getHeadingPath(), c.getContent())));
    }

    private static float[] parseVector(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode arr = JSON.readTree(json);
            float[] v = new float[arr.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = arr.get(i).floatValue();
            }
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- mapping ----

    private static KbRecord toRecord(KbEntity e) {
        return new KbRecord(e.getName(), e.getDescription(), e.getEmbeddingModel(),
                e.getEmbeddingDimensions(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static KbDocumentRecord toRecord(KbDocumentEntity e) {
        return new KbDocumentRecord(e.getId(), e.getKbName(), e.getDocPath(), e.getContentHash(),
                e.getSizeBytes(), KbDocumentStatus.valueOf(e.getStatus().name()), e.getErrorMessage(),
                e.getChunkCount(), e.getIngestedAt());
    }
}
