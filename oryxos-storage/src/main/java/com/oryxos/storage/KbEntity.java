package com.oryxos.storage;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for knowledge bases (data-model.md: kb_knowledge_bases).
 */
@Entity
@Table(name = "kb_knowledge_bases")
public class KbEntity {

    @Id
    @Column(name = "name")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Embedding identity recorded at first successful ingest (FR-015). */
    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(Integer embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
