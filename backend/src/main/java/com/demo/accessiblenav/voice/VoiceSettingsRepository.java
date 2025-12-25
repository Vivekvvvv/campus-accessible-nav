package com.demo.accessiblenav.voice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 语音设置仓库
 */
@Repository
public interface VoiceSettingsRepository extends JpaRepository<VoiceSettingsEntity, Long> {

    Optional<VoiceSettingsEntity> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
