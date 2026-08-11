package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for SessionEntity.
 */
@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    Optional<SessionEntity> findBySessionIdAndStatus(String sessionId, SessionEntity.SessionStatus status);

    List<SessionEntity> findByProfileName(String profileName);

    List<SessionEntity> findByStatus(SessionEntity.SessionStatus status);
}
