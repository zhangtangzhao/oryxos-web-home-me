package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for tool invocation audit records.
 */
@Repository
public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, Long> {

    List<ToolInvocationEntity> findBySessionId(String sessionId);

    List<ToolInvocationEntity> findByToolName(String toolName);
}
