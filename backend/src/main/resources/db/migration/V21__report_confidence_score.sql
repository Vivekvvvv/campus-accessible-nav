-- PR-11: Obstacle report confidence score
ALTER TABLE t_obstacle_report ADD COLUMN IF NOT EXISTS confidence_score DOUBLE PRECISION;
