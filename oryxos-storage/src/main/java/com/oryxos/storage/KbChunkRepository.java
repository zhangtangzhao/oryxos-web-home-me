package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for KbChunkEntity.
 */
@Repository
public interface KbChunkRepository extends JpaRepository<KbChunkEntity, Long> {

    List<KbChunkEntity> findByDocumentIdOrderByChunkOrdinal(Long documentId);

    List<KbChunkEntity> findByKbName(String kbName);

    long countByKbName(String kbName);
}
