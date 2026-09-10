package com.oryxos.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for KbEntity.
 */
@Repository
public interface KbRepository extends JpaRepository<KbEntity, String> {
}
