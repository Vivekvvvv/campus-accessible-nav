-- V12: Add PostGIS geography point to t_node for fast KNN snapping queries.
--
-- We use a generated stored column so:
-- - existing rows are backfilled automatically
-- - future inserts/updates stay consistent without application-side changes

ALTER TABLE IF EXISTS t_node
  ADD COLUMN IF NOT EXISTS geog public.geography(Point, 4326)
  GENERATED ALWAYS AS (
    ST_SetSRID(ST_MakePoint(lng::double precision, lat::double precision), 4326)::public.geography
  ) STORED;

CREATE INDEX IF NOT EXISTS idx_node_geog ON t_node USING GIST (geog);
