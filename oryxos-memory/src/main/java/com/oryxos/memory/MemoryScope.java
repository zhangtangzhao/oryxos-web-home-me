package com.oryxos.memory;

/**
 * Memory partition scope.
 * CORE: always loaded in full, never truncated.
 * ARCHIVAL: subject to truncation, used for recall queries.
 */
public enum MemoryScope {
    CORE,
    ARCHIVAL
}
