package com.iflytek.skillhub.domain.mcp;

import java.util.List;
import java.util.Optional;

public interface McpEntryRevisionRepository {
    Optional<McpEntryRevision> findById(Long id);
    Optional<McpEntryRevision> findByIdForUpdate(Long id);
    Optional<McpEntryRevision> findFirstByEntryIdOrderByRevisionNumberDesc(Long entryId);
    List<McpEntryRevision> findByEntryIdOrderByRevisionNumberDesc(Long entryId);
    McpEntryRevision save(McpEntryRevision revision);
    void delete(McpEntryRevision revision);
}
