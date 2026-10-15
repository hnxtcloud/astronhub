package com.iflytek.skillhub.domain.software;
import jakarta.persistence.*;

/** Application-specific anonymous update opt-in, absent rows mean disabled. */
@Entity
@Table(name = "software_update_source")
public class SoftwareUpdateSource {
    @Id private Long applicationId;
    @Column(nullable = false) private boolean anonymousEnabled;
    @Version private long entityVersion;
    protected SoftwareUpdateSource() { }
    public SoftwareUpdateSource(Long applicationId) { this.applicationId = applicationId; }
    public void setAnonymousEnabled(boolean enabled) { anonymousEnabled = enabled; }
    public Long getApplicationId() { return applicationId; }
    public boolean isAnonymousEnabled() { return anonymousEnabled; }
}
