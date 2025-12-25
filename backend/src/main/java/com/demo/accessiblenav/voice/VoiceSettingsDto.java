package com.demo.accessiblenav.voice;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 语音设置DTO
 */
public class VoiceSettingsDto {

    @Min(value = 0, message = "语速不能小于0.1")
    @Max(value = 10, message = "语速不能大于10")
    private Double rate;

    @Min(value = 0, message = "音调不能小于0")
    @Max(value = 2, message = "音调不能大于2")
    private Double pitch;

    @Min(value = 0, message = "音量不能小于0")
    @Max(value = 1, message = "音量不能大于1")
    private Double volume;

    private String lang;

    private String preferredVoice;

    private Boolean enabled;

    @Min(value = 0, message = "preTurnM must be >= 0")
    @Max(value = 500, message = "preTurnM must be <= 500")
    private Double preTurnM;

    @Min(value = 0, message = "preArrivalM must be >= 0")
    @Max(value = 1000, message = "preArrivalM must be <= 1000")
    private Double preArrivalM;

    @Min(value = 0, message = "announceIntervalM must be >= 0")
    @Max(value = 500, message = "announceIntervalM must be <= 500")
    private Double announceIntervalM;

    @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietHoursStart must match HH:mm")
    private String quietHoursStart;

    @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietHoursEnd must match HH:mm")
    private String quietHoursEnd;

    private Boolean vibrateEnabled;

    // Getters and Setters
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
}
