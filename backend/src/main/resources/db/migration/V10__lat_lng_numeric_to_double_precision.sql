-- V10: Align lat/lng (and related) column types with JPA mapping (Double -> float8).
-- Earlier migrations used NUMERIC/DECIMAL for some tables, which fails hibernate ddl-auto=validate on PostgreSQL.

ALTER TABLE IF EXISTS t_emergency_event
  ALTER COLUMN lat TYPE DOUBLE PRECISION USING lat::DOUBLE PRECISION,
  ALTER COLUMN lng TYPE DOUBLE PRECISION USING lng::DOUBLE PRECISION;

ALTER TABLE IF EXISTS t_volunteer
  ALTER COLUMN last_location_lat TYPE DOUBLE PRECISION USING last_location_lat::DOUBLE PRECISION,
  ALTER COLUMN last_location_lng TYPE DOUBLE PRECISION USING last_location_lng::DOUBLE PRECISION;

ALTER TABLE IF EXISTS t_facility
  ALTER COLUMN lat TYPE DOUBLE PRECISION USING lat::DOUBLE PRECISION,
  ALTER COLUMN lng TYPE DOUBLE PRECISION USING lng::DOUBLE PRECISION;

