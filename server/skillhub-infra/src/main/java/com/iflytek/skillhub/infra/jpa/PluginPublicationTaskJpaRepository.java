package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginPublicationTask;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginPublicationTaskJpaRepository
        extends JpaRepository<PluginPublicationTask, Long>, PluginPublicationTaskRepository {
    @org.springframework.data.jpa.repository.Query(
            """
select t from PluginPublicationTask t, PluginRelease r, PluginProject p, Namespace n
where t.releaseId=r.id and r.projectId=p.id and p.namespaceId=n.id
  and t.status <> 'SUCCEEDED' and r.status=com.iflytek.skillhub.domain.plugin.PluginReleaseStatus.PENDING_REVIEW
  and p.status=com.iflytek.skillhub.domain.plugin.PluginProjectStatus.ACTIVE and p.hidden=false
  and n.status=com.iflytek.skillhub.domain.namespace.NamespaceStatus.ACTIVE
  and ((t.completedChannels not like '%HOSTED%' and not exists (select h.id from PluginPublicationChannel h where h.taskId=t.id and h.channel='HOSTED')) or exists (
    select c.id from PluginPublicationChannel c where c.taskId=t.id and (
      (c.sourceDisabled=false and (c.nextAttemptAt is null or c.nextAttemptAt<=:now) and (c.state in ('QUEUED','FAILED','UNKNOWN') or (c.state='RUNNING' and c.leaseUntil<=:now)))
      or (c.state='SUCCEEDED' and t.completedChannels not like concat('%',c.channel,'%')))))
order by t.attemptCount asc, t.createdAt asc, t.id asc
""")
    java.util.List<PluginPublicationTask> findRunnable(
            java.time.Instant now, org.springframework.data.domain.Pageable pageable);
}
