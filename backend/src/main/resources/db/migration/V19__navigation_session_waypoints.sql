-- PR-09: Waypoints multi-leg navigation support
ALTER TABLE t_navigation_session ADD COLUMN IF NOT EXISTS waypoints_json TEXT;
ALTER TABLE t_navigation_session ADD COLUMN IF NOT EXISTS current_leg INT NOT NULL DEFAULT 0;
ALTER TABLE t_navigation_session ADD COLUMN IF NOT EXISTS total_legs INT NOT NULL DEFAULT 1;
