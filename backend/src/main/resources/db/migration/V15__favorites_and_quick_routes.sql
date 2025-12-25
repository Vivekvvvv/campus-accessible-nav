-- V15__favorites_and_quick_routes.sql
-- 云端收藏与快捷路线

CREATE TABLE IF NOT EXISTS t_favorite_group (
  id BIGSERIAL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT uk_favorite_group_user_name UNIQUE (user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_favorite_group_user ON t_favorite_group (user_id);

CREATE TABLE IF NOT EXISTS t_favorite_place (
  id BIGSERIAL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  group_id BIGINT NULL,
  name VARCHAR(128) NOT NULL,
  lat DOUBLE PRECISION NOT NULL,
  lng DOUBLE PRECISION NOT NULL,
  tags VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_favorite_place_group FOREIGN KEY (group_id) REFERENCES t_favorite_group (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_favorite_place_user ON t_favorite_place (user_id);
CREATE INDEX IF NOT EXISTS idx_favorite_place_group ON t_favorite_place (group_id);

CREATE TABLE IF NOT EXISTS t_quick_route (
  id BIGSERIAL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  start_place_id BIGINT NULL,
  end_place_id BIGINT NULL,
  travel_mode VARCHAR(16) NOT NULL DEFAULT 'WALK',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_quick_route_start_place FOREIGN KEY (start_place_id) REFERENCES t_favorite_place (id) ON DELETE SET NULL,
  CONSTRAINT fk_quick_route_end_place FOREIGN KEY (end_place_id) REFERENCES t_favorite_place (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_quick_route_user ON t_quick_route (user_id);
