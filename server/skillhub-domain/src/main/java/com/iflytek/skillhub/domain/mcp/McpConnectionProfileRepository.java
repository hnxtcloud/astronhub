package com.iflytek.skillhub.domain.mcp;

import java.util.List;

public interface McpConnectionProfileRepository {
    List<McpConnectionProfile> findByRevisionIdOrderByProfileKey(Long revisionId);
    McpConnectionProfile save(McpConnectionProfile profile);
}
