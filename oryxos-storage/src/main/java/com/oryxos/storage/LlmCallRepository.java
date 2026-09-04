package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for LLM call audit records.
 */
@Repository
public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Long> {

    List<LlmCallEntity> findBySessionId(String sessionId);

    List<LlmCallEntity> findByProvider(String provider);

    long countBySessionId(String sessionId);
}
