package com.oryxos.storage;

import jakarta.persistence.*;

/**
 * JPA entity for knowledge base chunks (data-model.md: kb_chunks).
 * embedding holds the vector as a JSON float array (TEXT) — brute-force
 * cosine at query time, no external vector store (research D3).
 */
@Entity
@Table(name = "kb_chunks",
       indexes = {
           @Index(name = "idx_kb_chunks_document", columnList = "document_id"),
           @Index(name = "idx_kb_chunks_kb", columnList = "kb_name")
       })
public class KbChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "kb_name", nullable = false)
    private String kbName;

    @Column(name = "chunk_ordinal", nullable = false)
    private Integer chunkOrdinal;

    /** Markdown heading path, e.g. "部署指南 > Windows" (> separators). */
    @Column(name = "heading_path", columnDefinition = "TEXT")
    private String headingPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** JSON float array of the embedding vector; null until ingest succeeds. */
    @Column(columnDefinition = "TEXT")
    private String embedding;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }
    public Integer getChunkOrdinal() { return chunkOrdinal; }
    public void setChunkOrdinal(Integer chunkOrdinal) { this.chunkOrdinal = chunkOrdinal; }
    public String getHeadingPath() { return headingPath; }
    public void setHeadingPath(String headingPath) { this.headingPath = headingPath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
}
