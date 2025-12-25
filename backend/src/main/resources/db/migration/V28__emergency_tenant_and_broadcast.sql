-- PR-16: volunteer/security linkage hardening + emergency broadcast history

ALTER TABLE t_emergency_event
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';

ALTER TABLE t_emergency_contact
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';

ALTER TABLE t_volunteer
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';

ALTER TABLE t_emergency_response
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_emergency_event_tenant ON t_emergency_event (tenant_id);
CREATE INDEX IF NOT EXISTS idx_emergency_contact_tenant ON t_emergency_contact (tenant_id);
CREATE INDEX IF NOT EXISTS idx_volunteer_tenant ON t_volunteer (tenant_id);
CREATE INDEX IF NOT EXISTS idx_emergency_response_tenant ON t_emergency_response (tenant_id);

CREATE TABLE IF NOT EXISTS t_emergency_broadcast (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT REFERENCES t_emergency_event(id),
    publisher_id VARCHAR(64) NOT NULL,
    target_scope VARCHAR(16) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    tenant_id VARCHAR(32) NOT NULL DEFAULT 'default',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_emergency_broadcast_tenant_created
    ON t_emergency_broadcast (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_emergency_broadcast_event
    ON t_emergency_broadcast (event_id);
