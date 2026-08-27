package com.iflytek.skillhub.domain.mcp;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface McpServerEntryRepository {
    Optional<McpServerEntry> findById(Long id);
    Optional<McpServerEntry> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    Page<McpServerEntry> findByStatusAndHiddenFalse(McpEntryStatus status, Pageable pageable);
    McpServerEntry save(McpServerEntry entry);
}
