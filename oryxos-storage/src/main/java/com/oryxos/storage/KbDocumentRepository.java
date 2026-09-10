package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for KbDocumentEntity.
 */
@Repository
public interface KbDocumentRepository extends JpaRepository<KbDocumentEntity, Long> {

    List<KbDocumentEntity> findByKbNameOrderByDocPath(String kbName);

    Optional<KbDocumentEntity> findByKbNameAndDocPath(String kbName, String docPath);

    List<KbDocumentEntity> findByKbNameAndStatus(String kbName, KbDocumentEntity.DocumentStatus status);

    long countByKbName(String kbName);
}
