-- Flyway migration V7: obstacle loop v2 (traceability + query indexes)

-- 1) Support "my reports" query: (submitter_id, created_at)
CREATE INDEX IF NOT EXISTS idx_obstacle_report_submitter_created
  ON t_obstacle_report (submitter_id, created_at DESC);

-- 2) Link effect to report and add audit fields (optional but helps traceability)
ALTER TABLE t_obstacle_effect ADD COLUMN IF NOT EXISTS report_id BIGINT;
ALTER TABLE t_obstacle_effect ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NULL;
ALTER TABLE t_obstacle_effect ADD COLUMN IF NOT EXISTS created_by VARCHAR(64) NULL;
ALTER TABLE t_obstacle_effect ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP NULL;
ALTER TABLE t_obstacle_effect ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(64) NULL;

-- Add FK constraint (PostgreSQL). For existing data, report_id may be NULL.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_obstacle_effect_report'
  ) THEN
    ALTER TABLE t_obstacle_effect
      ADD CONSTRAINT fk_obstacle_effect_report
      FOREIGN KEY (report_id) REFERENCES t_obstacle_report (id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_obstacle_effect_report_id ON t_obstacle_effect (report_id);

