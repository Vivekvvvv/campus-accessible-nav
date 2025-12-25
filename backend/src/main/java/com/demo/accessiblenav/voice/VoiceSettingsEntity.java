package com.demo.accessiblenav.voice;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 用户语音设置实体
 */
@Entity
@Table(name = "t_voice_settings")
public class VoiceSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(nullable = false)
    private Double rate = 1.0;

    @Column(nullable = false)
    private Double pitch = 1.0;

    @Column(nullable = false)
    private Double volume = 0.8;

    @Column(length = 10)
    private String lang = "zh-CN";

    @Column(name = "preferred_voice", length = 128)
    private String preferredVoice;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "pre_turn_m", nullable = false)
    private Double preTurnM = 12.0;

    @Column(name = "pre_arrival_m", nullable = false)
    private Double preArrivalM = 20.0;

    @Column(name = "announce_interval_m", nullable = false)
    private Double announceIntervalM = 20.0;

    @Column(name = "quiet_hours_start", length = 5)
    private String quietHoursStart;

    @Column(name = "quiet_hours_end", length = 5)
    private String quietHoursEnd;

    @Column(name = "vibrate_enabled", nullable = false)
    private Boolean vibrateEnabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }

    public Double getPitch() { return pitch; }
    public void setPitch(Double pitch) { this.pitch = pitch; }

    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getPreferredVoice() { return preferredVoice; }
    public void setPreferredVoice(String preferredVoice) { this.preferredVoice = preferredVoice; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Double getPreTurnM() {
        return preTurnM;
    }

    public void setPreTurnM(Double preTurnM) {
        this.preTurnM = preTurnM;
    }

    public Double getPreArrivalM() {
        return preArrivalM;
    }

    public void setPreArrivalM(Double preArrivalM) {
        this.preArrivalM = preArrivalM;
    }

    public Double getAnnounceIntervalM() {
        return announceIntervalM;
    }

    public void setAnnounceIntervalM(Double announceIntervalM) {
        this.announceIntervalM = announceIntervalM;
    }

    public String getQuietHoursStart() {
        return quietHoursStart;
    }

    public void setQuietHoursStart(String quietHoursStart) {
        this.quietHoursStart = quietHoursStart;
    }

    public String getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHoursEnd(String quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd;
    }

    public Boolean getVibrateEnabled() {
        return vibrateEnabled;
    }

    public void setVibrateEnabled(Boolean vibrateEnabled) {
        this.vibrateEnabled = vibrateEnabled;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
