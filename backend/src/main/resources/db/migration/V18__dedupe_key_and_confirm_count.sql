-- PR-05: Duplicate report detection
ALTER TABLE t_obstacle_report ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(64);
ALTER TABLE t_obstacle_report ADD COLUMN IF NOT EXISTS confirm_count INT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_obstacle_dedupe ON t_obstacle_report (dedupe_key);
