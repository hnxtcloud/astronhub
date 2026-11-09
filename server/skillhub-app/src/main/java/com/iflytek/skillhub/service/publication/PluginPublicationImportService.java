package com.iflytek.skillhub.service.publication;

import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.dto.plugin.PluginImportDtos.*;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.PluginOfflineImportAppService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates per-candidate commits; identical content is reconciled by the existing import workflow. */
@Service
public class PluginPublicationImportService {
    private final PluginOfflineImportAppService imports;
    public PluginPublicationImportService(PluginOfflineImportAppService imports) { this.imports = imports; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportResultView apply(String batch, ImportSelection selection, PluginActor actor, AuditRequestContext audit) {
        return imports.apply(batch, new ImportSelectionRequest(List.of(selection)), actor, audit).results().getFirst();
    }
}
