-- Flyway migration V3: add performance optimization indexes

-- 复合索引优化路径查询
CREATE INDEX IF NOT EXISTS idx_edge_from_to_composite ON t_edge (from_node_id, to_node_id);

-- 无障碍路径查询优化索引
CREATE INDEX IF NOT EXISTS idx_edge_accessible ON t_edge (is_accessible_default, has_stairs, slope_level);

-- 节点位置复合索引
CREATE INDEX IF NOT EXISTS idx_node_location ON t_node (lat, lng);

-- 障碍效果活跃查询优化
CREATE INDEX IF NOT EXISTS idx_obstacle_effect_active_time ON t_obstacle_effect (active, end_at) WHERE active = true;

-- 障碍上报按边查询优化
CREATE INDEX IF NOT EXISTS idx_obstacle_report_edge_status ON t_obstacle_report (edge_id, status);

-- 用户账户角色查询优化
CREATE INDEX IF NOT EXISTS idx_user_accounts_role ON user_accounts (role);

-- 操作日志按操作者查询优化
CREATE INDEX IF NOT EXISTS idx_operation_logs_actor ON operation_logs (actor);

-- 图变更请求创建者查询优化
CREATE INDEX IF NOT EXISTS idx_graph_change_created_by ON t_graph_change_request (created_by);

-- 图快照版本降序索引（用于获取最新版本）
CREATE INDEX IF NOT EXISTS idx_graph_snapshot_version_desc ON t_graph_snapshot (version DESC);
