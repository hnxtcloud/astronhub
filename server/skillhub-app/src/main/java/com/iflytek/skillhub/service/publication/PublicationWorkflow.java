package com.iflytek.skillhub.service.publication;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.publication.PublicationTask;
import com.iflytek.skillhub.dto.publication.PublicationDraft;
import com.iflytek.skillhub.service.AuditRequestContext;

/** One checkpointed application operation, with validation delegated to its owning domain. */
public interface PublicationWorkflow {
    String type();
    default void preview(PublicationTask task, PublicationDraft draft, ObjectNode progress,
            PluginActor actor, AuditRequestContext audit) throws java.io.IOException {}
    default void cancel(PublicationTask task, ObjectNode progress, String owner) {}
    String advance(PublicationTask task, PublicationDraft draft, ObjectNode progress,
            PluginActor actor, AuditRequestContext audit) throws java.io.IOException;
}
