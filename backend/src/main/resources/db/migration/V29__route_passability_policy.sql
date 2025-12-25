-- PR-17: Dynamic passability penalty policy per tenant

CREATE TABLE IF NOT EXISTS t_route_passability_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(32) NOT NULL UNIQUE,
    passability_penalty_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    passability_min_clamp DOUBLE PRECISION NOT NULL DEFAULT 0.01,
    passability_weight_factor DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_route_passability_policy_tenant
    ON t_route_passability_policy (tenant_id);
