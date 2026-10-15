package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Immutable approved target plan and durable channel receipt; no secret material. */
@Entity
@Table(
        name = "plugin_publication_channel",
        uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "channel"}))
public class PluginPublicationChannel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(nullable = false, length = 32)
    private String state = "QUEUED";

    @Column(name = "attempt_token", length = 36)
    private String attemptToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 80)
    private String lastError;

    @Column(columnDefinition = "TEXT")
    private String receipt;

    @Column(name="active_snapshot",columnDefinition="TEXT") private String activeSnapshot;
    @Column(name="plan_revision",nullable=false) private int planRevision;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="last_claimed_at") private Instant lastClaimedAt;
    @Column(name="source_disabled",nullable=false) private boolean sourceDisabled;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PluginPublicationChannel() {}

    public PluginPublicationChannel(Long taskId, String channel, String snapshot) {
        this.taskId = taskId;
        this.channel = channel;
        this.snapshot = snapshot;
        if (snapshot == null || snapshot.equals("MISSING_CONFIGURATION")) {
            this.snapshot = "MISSING_CONFIGURATION";
            state = "BLOCKED";
            lastError = "MISSING_CONFIGURATION";
        }
    }

    public String claim(Instant now) {
        return claim(now,300);
    }
    public String claim(Instant now,int leaseSeconds) {
        if (state.equals("SUCCEEDED")
                || state.equals("BLOCKED")
                || sourceDisabled
                || (nextAttemptAt!=null&&nextAttemptAt.isAfter(now))
                || (state.equals("RUNNING") && leaseUntil.isAfter(now))) return null;
        attemptToken = UUID.randomUUID().toString();
        state = "RUNNING";
        leaseUntil = now.plusSeconds(leaseSeconds);
        lastClaimedAt=now;
        attemptCount++;
        return attemptToken;
    }

    public boolean finish(String token, String result, String error) {
        if (!state.equals("RUNNING") || !java.util.Objects.equals(token, attemptToken))
            return false;
        if (error == null) {
            state = "SUCCEEDED";
            receipt = result;
            lastError = null;
        } else {
            state = "FAILED";
            lastError = error;
        }
        leaseUntil = null;
        return true;
    }

    public boolean heartbeat(String token,int revision,Instant now,int seconds){if(!owns(token,revision,now))return false;leaseUntil=now.plusSeconds(seconds);return true;}
    public boolean owns(String token,int revision,Instant now){return state.equals("RUNNING")&&revision==planRevision&&java.util.Objects.equals(token,attemptToken)&&leaseUntil!=null&&leaseUntil.isAfter(now);}
    public boolean finish(String token,int revision,Instant now,String result,String error,boolean unknown){
        if(!owns(token,revision,now))return false;
        boolean accepted=finish(token,result,error);
        if(error!=null){state=unknown?"UNKNOWN":"FAILED";nextAttemptAt=now.plusSeconds(Math.min(300,Math.max(5,attemptCount*5L)));}
        return accepted;
    }
    public void replan(int expected,String replacement){
        if(expected!=planRevision)throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException("error.plugin.recoveryConflict");
        if(!java.util.Set.of("BLOCKED","FAILED").contains(state)||replacement==null||replacement.isBlank()||replacement.equals("MISSING_CONFIGURATION"))throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException("error.plugin.recoveryState");
        activeSnapshot=replacement;planRevision++;state="QUEUED";lastError=null;attemptToken=null;leaseUntil=null;nextAttemptAt=null;
    }
    public void retryNow(){if(!state.equals("SUCCEEDED")&&!state.equals("RUNNING"))nextAttemptAt=null;}
    public void disableSource(){sourceDisabled=true;}
    public int getPlanRevision(){return planRevision;}public String getEffectiveSnapshot(){return activeSnapshot==null?snapshot:activeSnapshot;}
    public Instant getLeaseUntil(){return leaseUntil;}public String getAttemptToken(){return attemptToken;}public Instant getLastClaimedAt(){return lastClaimedAt;}public Instant getNextAttemptAt(){return nextAttemptAt;}public boolean isSourceDisabled(){return sourceDisabled;}

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getChannel() {
        return channel;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getState() {
        return state;
    }

    public String getReceipt() {
        return receipt;
    }

    public String getLastError() {
        return lastError;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
