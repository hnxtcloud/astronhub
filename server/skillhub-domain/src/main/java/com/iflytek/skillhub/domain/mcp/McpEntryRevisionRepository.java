package com.iflytek.skillhub.domain.mcp;

import java.util.List;
import java.util.Optional;

public interface McpEntryRevisionRepository {
    Optional<McpEntryRevision> findById(Long id);
    List<McpEntryRevision> findByEntryIdOrderByRevisionNumberDesc(Long entryId);
    McpEntryRevision save(McpEntryRevision revision);
}
