-- PR-14: Multi-tenant isolation - add tenant_id to all main tables
ALTER TABLE t_node ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';
ALTER TABLE t_edge ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';
ALTER TABLE t_obstacle_report ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';
ALTER TABLE t_obstacle_effect ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';
ALTER TABLE t_navigation_session ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';
ALTER TABLE t_building ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_node_tenant ON t_node (tenant_id);
CREATE INDEX IF NOT EXISTS idx_edge_tenant ON t_edge (tenant_id);
CREATE INDEX IF NOT EXISTS idx_obstacle_report_tenant ON t_obstacle_report (tenant_id);
CREATE INDEX IF NOT EXISTS idx_obstacle_effect_tenant ON t_obstacle_effect (tenant_id);
CREATE INDEX IF NOT EXISTS idx_navigation_session_tenant ON t_navigation_session (tenant_id);
CREATE INDEX IF NOT EXISTS idx_building_tenant ON t_building (tenant_id);
