ALTER TABLE operation_logs ADD COLUMN actor_role VARCHAR(32);
CREATE INDEX idx_operation_logs_actor_role ON operation_logs (actor_role);
