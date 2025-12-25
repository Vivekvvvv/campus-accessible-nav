-- V9: Align emergency_event.accuracy column type with JPA mapping (Double -> float8).
-- Existing installs may have NUMERIC(10,2) from V5; this converts safely.

ALTER TABLE IF EXISTS t_emergency_event
  ALTER COLUMN accuracy TYPE DOUBLE PRECISION
  USING accuracy::DOUBLE PRECISION;

