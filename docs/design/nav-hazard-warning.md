# 导航：障碍/危险点提前预警（effect）mini 设计稿

目标读者：研发（后端/前端/运维）

## 1. 背景与目标

现状：
- 障碍上报审核通过后会生成 `t_obstacle_effect`（disabled/active/endAt/reason），路由计算会避开 disabled 的边。
- 但“会话开始后才出现的新 effect”可能导致用户仍沿旧路线前进，直到偏航触发或手动重算。

目标（用户侧）：
- 在导航会话进行中，识别“当前会话路线附近”的 active effect，并在接近时给予提示（toast + 可选语音/震动）。
- 提供“一键避障重算”。

非目标（本版不做）：
- 不做复杂的“障碍可信度/风险等级”模型。
- 不做严格的“effect 一定在路径边集上”的精确匹配（当前 route 不暴露 edgeIds，采用几何近似）。

## 2. API 设计

### 2.1 查询会话路线附近的 hazards

`GET /api/navigation/session/{id}/hazards?radiusM=30&limit=30`

鉴权：
- 需要登录；owner 校验（只能访问自己的 session）

返回：
```json
[
  {
    "effectId": 123,
    "edgeId": 456,
    "reason": "blocked",
    "endAt": "2026-02-05T12:00:00Z",
    "fromLat": 23.275,
    "fromLng": 113.200,
    "toLat": 23.27505,
    "toLng": 113.20005,
    "distanceToRouteM": 0.0,
    "routeAtM": 42.3
  }
]
```

语义：
- `distanceToRouteM`：effect 对应边的“中点”到会话路线 polyline 的最小距离（米）
- `routeAtM`：该 hazard 在路线上的“最近点”对应的沿路线距离（米，从路线起点开始累计）

## 3. 表结构/数据来源

不新增表：
- `t_obstacle_effect`：hazard 来源（active=true 且 disabled=true 且未过期）
- `t_edge` + `t_node`：拼接出边的端点坐标
- `navigation_session.route_json`：会话当前路线（RouteResponse.path）

## 4. 后端实现要点

文件：
- `backend/src/main/java/com/demo/accessiblenav/navigation/api/NavigationSessionController.java`
- `backend/src/main/java/com/demo/accessiblenav/navigation/service/NavigationSessionAppService.java`
- `backend/src/main/java/com/demo/accessiblenav/obstacle/ObstacleEffectRepository.java`

算法（近似，校园尺度足够）：
1) 读取 session.route_json，取 `path` 作为 route polyline
2) 计算 route bbox（min/max lat/lng），按 `radiusM` 转换为度数缓冲后扩大 bbox
3) 查询 bbox 内 endpoints 命中的 active disabled effects（未过期），join fetch edge/fromNode/toNode
4) 对每条 effect 取边中点 (midLat/midLng)，用等距矩形投影（equirectangular）投影到 route polyline：
   - 得到 `distanceToRouteM`、`routeAtM`
   - 过滤 `distanceToRouteM <= radiusM`
5) 返回按 `distanceToRouteM` 升序排序的前 `limit` 条

## 5. 前端状态与交互

文件：
- `frontend/src/stores/useNavigationSessionStore.js`
- `frontend/src/stores/useNavStore.js`
- `frontend/src/components/NavigationPanel.vue`
- i18n：`frontend/src/locales/zh-CN.js` / `frontend/src/locales/en-US.js`

状态：
- `hazards: []`：后台计算出来的 hazards 列表（轮询刷新）
- `hazardWarning: { ...hazard, remainingM } | null`：当前“触发提示”的 hazard（用于 UI + 一键重算）
- `warnedHazardEffectIds: Set`：去重（同一个 effectId 只提示一次）

触发规则（默认）：
- 会话 ACTIVE 时每 `15s` 拉一次 hazards
- 每次定位更新后：
  - 计算用户在路线上的 `alongM`
  - 找 `0 <= hazard.routeAtM - alongM <= 25m` 的最近 hazard
  - 如果未提示过 → toast + emitEvent（语音/震动由现有策略决定）

一键重算：
- `NavigationPanel` 在出现 `hazardWarning` 时展示按钮
- 点击后调用 `rerouteFromLocation(userLocation, 'OBSTACLE')`
  - 该 reason 会体现在后端指标 `navigation.reroutes{reason="OBSTACLE"}` 中

## 6. 测试与回归

后端：
- 新增集成测试：`backend/src/test/java/com/demo/accessiblenav/navigation/NavigationSessionControllerIntegrationTest.java`
  - 创建 session 后再插入 effect，验证 `/hazards` 能返回并包含对应 edgeId

前端（已落地）：
- e2e：`frontend/tests/navigation.spec.js` 已覆盖“会话中出现 hazard 提示并一键重算（reason=OBSTACLE）”主链路。

边界用例 Checklist（可直接按项执行）：
- [x] 主链路：出现 hazard 提示后可一键重算（`reason=OBSTACLE`）
- [x] 同一 `effectId` 去重：同一路段重复轮询不应重复弹 toast（已补 e2e）
- [x] 冷却窗口：冷却时间内重复 hazard 不应重复触发播报/震动（已补 e2e）
- [x] 并发场景：连续偏航自动重算与 hazard 提示并发时，不应出现状态错乱（按钮消失/重复请求）（已补 e2e）
- [x] 会话恢复场景：刷新/断网恢复后，`warnedHazardEffectIds` 与 `hazards` 状态一致且不误报（已补 e2e）
- [x] 多途经点场景：waypoints 切段后，`routeAtM` 与提示距离仍正确（已补 e2e）

每项验收标准（统一）：
- 触发次数与预期一致（可通过 `navigation_client_events_total` 与网络请求计数交叉验证）
- UI 状态稳定（`hazardWarning`、重算按钮、toast 文案）
- 无重复无效请求（`/hazards` 轮询频率与 `/reroute` 调用次数符合预期）

## 7. 指标与告警

新增指标（Micrometer counter）：
- `navigation.hazards.queries_total`
- `navigation.hazards.matched_total`

已有可复用指标：
- `navigation.reroutes_total{reason="OBSTACLE"}`（一键避障重算效果）

建议 Grafana 面板新增：
- hazards queries / matched rate
- reroutes by reason（包含 OBSTACLE）
- hazards matched / active session（强度）
