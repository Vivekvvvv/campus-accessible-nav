-- PR-12: Review SLA escalation support
ALTER TABLE t_obstacle_report ADD COLUMN IF NOT EXISTS escalated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE t_obstacle_report ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMP;
ALTER TABLE t_obstacle_report ADD COLUMN IF NOT EXISTS assigned_reviewer VARCHAR(64);
