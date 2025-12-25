-- V6__create_voice_settings_table.sql
-- 语音设置表

CREATE TABLE t_voice_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    rate NUMERIC(3, 1) NOT NULL DEFAULT 1.0,
    pitch NUMERIC(3, 1) NOT NULL DEFAULT 1.0,
    volume NUMERIC(3, 1) NOT NULL DEFAULT 0.8,
    lang VARCHAR(10) DEFAULT 'zh-CN',
    preferred_voice VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_voice_settings_user ON t_voice_settings(user_id);
