package com.oryxos.storage;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for knowledge base documents (data-model.md: kb_documents).
 * Lifecycle: add → PENDING → READY | FAILED; FAILED is retried by re-running
 * ingest (back to PENDING semantics at service level).
 */
@Entity
@Table(name = "kb_documents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"kb_name", "doc_path"}))
public class KbDocumentEntity {

    public enum DocumentStatus { PENDING, READY, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_name", nullable = false)
    private String kbName;

    @Column(name = "doc_path", nullable = false)
    private String docPath;

    /** sha256 hex of document content — the incremental-ingest fingerprint (FR-008). */
    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }
    public String getDocPath() { return docPath; }
    public void setDocPath(String docPath) { this.docPath = docPath; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public Instant getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(Instant ingestedAt) { this.ingestedAt = ingestedAt; }
}
