-- PR-17: Edge passability probability model
ALTER TABLE t_edge ADD COLUMN IF NOT EXISTS passability_probability DOUBLE PRECISION NOT NULL DEFAULT 1.0;
