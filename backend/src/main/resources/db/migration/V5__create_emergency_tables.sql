-- V5__create_emergency_tables.sql
-- 紧急求助相关表

-- 紧急事件表
CREATE TABLE t_emergency_event (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(128),
    event_type VARCHAR(32) NOT NULL,  -- FALL, LOST, MEDICAL, OTHER
    description TEXT,
    lat NUMERIC(10, 7) NOT NULL,
    lng NUMERIC(10, 7) NOT NULL,
    accuracy NUMERIC(10, 2),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, RESPONDING, RESOLVED, CANCELLED
    handled_by VARCHAR(64),
    handled_at TIMESTAMP,
    resolution_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 紧急联系人表
CREATE TABLE t_emergency_contact (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(128),
    relationship VARCHAR(32),  -- FAMILY, FRIEND, GUARDIAN
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 志愿者表
CREATE TABLE t_volunteer (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    phone_number VARCHAR(20),
    is_active BOOLEAN DEFAULT true,
    last_location_lat NUMERIC(10, 7),
    last_location_lng NUMERIC(10, 7),
    last_location_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 紧急事件响应记录
CREATE TABLE t_emergency_response (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES t_emergency_event(id),
    responder_type VARCHAR(16) NOT NULL,  -- SECURITY, VOLUNTEER, CONTACT
    responder_id VARCHAR(64),
    responder_name VARCHAR(64),
    response_time TIMESTAMP NOT NULL DEFAULT NOW(),
    arrival_time TIMESTAMP,
    notes TEXT
);

-- 索引
CREATE INDEX idx_emergency_event_user ON t_emergency_event(user_id);
CREATE INDEX idx_emergency_event_status ON t_emergency_event(status);
CREATE INDEX idx_emergency_event_created ON t_emergency_event(created_at);
CREATE INDEX idx_emergency_contact_user ON t_emergency_contact(user_id);
CREATE INDEX idx_volunteer_active ON t_volunteer(is_active);
CREATE INDEX idx_volunteer_location ON t_volunteer(last_location_lat, last_location_lng);
