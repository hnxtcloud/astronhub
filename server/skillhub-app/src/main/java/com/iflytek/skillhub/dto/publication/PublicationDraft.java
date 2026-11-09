package com.iflytek.skillhub.dto.publication;

import com.iflytek.skillhub.dto.plugin.PluginImportDtos.ImportSelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/** User-editable metadata only; execution receipts and storage keys are never accepted. */
public record PublicationDraft(
        @Size(max = 16) String type, @Size(max = 64) String namespace,
        @Size(max = 24) String visibility, @Size(max = 100) String slug,
        @Size(max = 200) String name, @Size(max = 128) String version,
        @Size(max = 20000) String description, Long appId, Long projectId,
        @Size(max = 32) String targetOs, @Size(max = 32) String targetArch,
        @Size(max = 64) String targetDistro, @Size(max = 16) String format,
        boolean confirmWarnings, boolean repository,
        @Size(max = 1000) List<@Valid ImportSelection> selections,
        @Size(max = 32000) String rawVersionFields,
        Long targetSkillId, Long npmRegistryId) {}
