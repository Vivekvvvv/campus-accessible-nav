package com.demo.accessiblenav.voice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 语音设置服务
 */
@Service
public class VoiceSettingsService {

    private final VoiceSettingsRepository repository;

    public VoiceSettingsService(VoiceSettingsRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取用户语音设置
     */
    public VoiceSettingsDto getUserSettings(String userId) {
        VoiceSettingsEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        return toDto(entity);
    }

    /**
     * 更新用户语音设置
     */
    @Transactional
    public VoiceSettingsDto updateSettings(String userId, VoiceSettingsDto dto) {
        VoiceSettingsEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> {
                    VoiceSettingsEntity newEntity = new VoiceSettingsEntity();
                    newEntity.setUserId(userId);
                    return newEntity;
                });

        if (dto.getRate() != null) {
            entity.setRate(dto.getRate());
        }
        if (dto.getPitch() != null) {
            entity.setPitch(dto.getPitch());
        }
        if (dto.getVolume() != null) {
            entity.setVolume(dto.getVolume());
        }
        if (dto.getLang() != null) {
            entity.setLang(dto.getLang());
        }
        if (dto.getPreferredVoice() != null) {
            entity.setPreferredVoice(dto.getPreferredVoice());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getPreTurnM() != null) {
            entity.setPreTurnM(dto.getPreTurnM());
        }
        if (dto.getPreArrivalM() != null) {
            entity.setPreArrivalM(dto.getPreArrivalM());
        }
        if (dto.getAnnounceIntervalM() != null) {
            entity.setAnnounceIntervalM(dto.getAnnounceIntervalM());
        }
        if (dto.getQuietHoursStart() != null) {
            entity.setQuietHoursStart(trimToNull(dto.getQuietHoursStart()));
        }
        if (dto.getQuietHoursEnd() != null) {
            entity.setQuietHoursEnd(trimToNull(dto.getQuietHoursEnd()));
        }
        if (dto.getVibrateEnabled() != null) {
            entity.setVibrateEnabled(dto.getVibrateEnabled());
        }

        entity = Objects.requireNonNull(repository.save(Objects.requireNonNull(entity)));
        return toDto(entity);
    }

    /**
     * 创建默认设置
     */
    private VoiceSettingsEntity createDefaultSettings(String userId) {
        VoiceSettingsEntity entity = new VoiceSettingsEntity();
        entity.setUserId(userId);
        return Objects.requireNonNull(repository.save(entity));
    }

    private VoiceSettingsDto toDto(VoiceSettingsEntity entity) {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setRate(entity.getRate());
        dto.setPitch(entity.getPitch());
        dto.setVolume(entity.getVolume());
        dto.setLang(entity.getLang());
        dto.setPreferredVoice(entity.getPreferredVoice());
        dto.setEnabled(entity.getEnabled());
        dto.setPreTurnM(entity.getPreTurnM());
        dto.setPreArrivalM(entity.getPreArrivalM());
        dto.setAnnounceIntervalM(entity.getAnnounceIntervalM());
        dto.setQuietHoursStart(entity.getQuietHoursStart());
        dto.setQuietHoursEnd(entity.getQuietHoursEnd());
        dto.setVibrateEnabled(entity.getVibrateEnabled());
        return dto;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
