-- Obstacle loop v2: keep full effect history while enforcing "one active effect per edge".
-- Replace (edge_id, active) uniqueness with a Postgres partial unique index for active=true only.

-- Drop legacy uniqueness (it also prevented multiple inactive history rows).
DROP INDEX IF EXISTS uk_effect_edge_active;

-- Enforce: each edge can have at most 1 active=true effect at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uk_effect_edge_active_true
  ON t_obstacle_effect(edge_id)
  WHERE active = true;

-- Helpful query indexes for lifecycle + admin views.
CREATE INDEX IF NOT EXISTS idx_effect_report_id ON t_obstacle_effect(report_id);
CREATE INDEX IF NOT EXISTS idx_effect_active_end_at ON t_obstacle_effect(active, end_at);

