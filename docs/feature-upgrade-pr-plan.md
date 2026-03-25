# 功能拓展升级 PR 清单（P0/P1/P2）

更新时间：2026-03-25
适用范围：`backend/` + `frontend/` + `monitoring/` + `RUNBOOK.md`

## 1. 目标与原则

- 目标：把“导航个性化、智能提醒、上报增强、搜索增强”做成可上线闭环，再平滑进入 P1/P2 平台化能力。
- 原则：
  - 每个 PR 可独立回滚，不做超大合并。
  - 每个 PR 必须包含：DB 变更（如有）、API 契约、前端状态、测试、指标。
  - 默认加功能开关（feature flag），先灰度再全量。

## 2. 里程碑与节奏

- `V1 / P0（1-2 个月）`：用户直接可感知能力，优先交付。
- `V2 / P1（2-4 个月）`：形成产品壁垒，补完会话与审核闭环。
- `V3 / P2（4-8 个月）`：平台化与多租户能力。

## 3. V1（P0）可开发 PR 清单

> 进度备注（2026-03-25）：
> - PR-01 已落地：用户无障碍偏好档案完整实现（profile 模块 + 前端 SettingsPanel 偏好区块 + 单测/IT）。
> - PR-02 已落地：路由权重策略完整实现（strategyWeights + route passability policy + 管理接口）。
> - PR-04 已落地：障碍上报照片上传 + PostGIS KNN 自动反查边已实现。
> - PR-05 已落地：重复上报检测与合并（DedupeKeyGenerator + CredibilityScorer）已实现。
> - PR-08 已落地：监控告警核心规则已部署。
> - PR-03 待收口：语音提醒策略字段已预留（VoiceSettingsEntity），播报节流/夜间静音前端逻辑待完善。
> - PR-06 待收口：收藏基础 CRUD 已实现，分组 + 常用路线一键发起待补充。
> - PR-07 待收口：hazard 设计稿已完成（docs/design/nav-hazard-warning.md），后端 API + 前端预警交互待实现。

## 3.1 PR-01 用户无障碍偏好档案（轮椅/视障/推车）

- 范围：
  - 新增用户偏好档案与版本字段，支持按用户读取默认导航偏好。
- DB（Flyway，建议 `V14__user_accessibility_profile.sql`）：
  - `user_accessibility_profile`：
    - `id`, `user_id`(unique), `mobility_mode`(`WALK/WHEELCHAIR/STROLLER/VISUAL_ASSIST`)
    - `avoid_stairs`, `avoid_slope`, `avoid_construction`（bool）
    - `max_slope_percent`（numeric）
    - `created_at`, `updated_at`
- API：
  - `GET /api/profile/accessibility`
  - `PUT /api/profile/accessibility`
- 前端状态：
  - `useSessionStore` 增加 `accessibilityProfile`
  - 设置面板新增“出行档案”区块。
- 测试：
  - 后端：Controller + Service + PG IT（唯一约束与更新覆盖）。
  - 前端：store 单测 + 表单保存 e2e smoke。
- 指标：
  - `profile_accessibility_update_total{mode}`
- 回滚：
  - 保留旧默认策略；关掉开关后路由回退原逻辑。

## 3.2 PR-02 路由权重策略（避台阶/避坡/避施工）

- 范围：
  - 路由请求支持带偏好参数，并与障碍 effect 联动权重。
- API：
  - 扩展 `POST /api/route` 请求：`strategyWeights`（可选）
  - 导航会话创建时自动带入用户偏好。
- 后端实现：
  - `GraphRoutingService` 增加权重计算器（stairs/slope/construction）。
  - 默认权重在配置中定义（可热更新优先，至少支持重启生效）。
- 测试：
  - PG IT：同起终点在不同权重下路径变化可复现。
  - 回归：原默认请求路径不回归。
- 指标：
  - `route_strategy_selected_total{mode,stairs,slope,construction}`
  - `route_weight_penalty_seconds_sum{reason}`
- 回滚：
  - feature flag：`routing.weight.strategy.enabled=false`

## 3.3 PR-03 智能提醒策略（提前量/夜间静音/播报节流）

- 范围：
  - 补齐语音与振动策略配置，支持每 20m/转向/偏航才播报。
- DB（建议 `V15__voice_reminder_policy.sql`）：
  - 可复用 `voice_settings` 扩展字段：
    - `pre_turn_m`, `pre_arrival_m`, `announce_interval_m`
    - `quiet_hours_start`, `quiet_hours_end`, `vibrate_enabled`
- API：
  - 扩展 `GET/PUT /api/v1/voice-settings`
- 前端状态：
  - `useNavStore` + `useNavigationSessionStore` 执行节流门控。
  - UI：策略设置、夜间静音开关、震动开关。
- 测试：
  - 前端：节流逻辑单测（20m/转向/偏航触发）。
  - 后端：设置存取与边界值校验。
- 指标：
  - `navigation_client_events_total{type="VOICE_ANNOUNCED|VIBRATE_TRIGGERED|QUIET_HOUR_SUPPRESSED"}`
- 回滚：
  - 前端退回旧播报策略；后端字段兼容可忽略。

## 3.4 PR-04 障碍上报照片上传 + 自动反查边/路段

- 范围：
  - 上报支持多图证据，自动匹配最近边提升审核效率。
- DB（建议 `V16__obstacle_attachments_and_edge_match.sql`）：
  - `obstacle_report_attachment`：`report_id`, `file_url`, `content_type`, `size_bytes`
  - `t_obstacle_report` 新增：
    - `matched_edge_id`, `match_distance_m`, `match_confidence`
- API：
  - `POST /api/obstacles/report` 支持 `lat/lng + photos[]`
  - 或保留 `files/upload` 后在 report 中提交 `fileIds`
- 后端实现：
  - PostGIS KNN 最近边反查（限定半径）。
- 测试：
  - PG IT：自动匹配准确性与半径外兜底。
  - 安全：文件类型/大小限制。
- 指标：
  - `obstacle_report_attachment_total`
  - `obstacle_report_edge_match_success_total`
- 回滚：
  - 匹配失败降级为人工选择边；附件不影响核心上报流程。

## 3.5 PR-05 重复上报检测与合并（保留证据与次数）

- 范围：
  - 同位置/同类型/短时间窗口内重复上报聚合。
- DB（建议 `V17__obstacle_report_dedupe.sql`）：
  - `t_obstacle_report` 新增：
    - `dedupe_key`, `merged_into_report_id`, `evidence_count`
  - 索引：`(dedupe_key, created_at desc)`
- 后端实现：
  - 规则：距离阈值 + 类型 + 时间窗口（如 30 分钟）
  - 合并时保留附件与来源用户列表。
- 测试：
  - IT：重复上报被合并；跨窗口不合并。
- 指标：
  - `obstacle_report_deduped_total`
  - `obstacle_report_evidence_count_histogram`
- 回滚：
  - 关闭 dedupe 开关，保持单条上报写入。

## 3.6 PR-06 收藏分组 + 常用路线一键发起

- 范围：
  - 从前端 localStorage 走向后端持久化（可灰度双写）。
- DB（建议 `V18__favorites_and_quick_routes.sql`）：
  - `favorite_group`：`user_id`, `name`, `sort_order`
  - `favorite_place`：`group_id`, `name`, `lat`, `lng`, `tags`
  - `quick_route`：`user_id`, `name`, `start_place_id`, `end_place_id`, `travel_mode`
- API：
  - `GET/POST/PUT/DELETE /api/favorites/groups`
  - `GET/POST/PUT/DELETE /api/favorites/places`
  - `GET/POST/DELETE /api/favorites/quick-routes`
- 前端状态：
  - `useFavoritesStore` 改为远端优先，本地兜底缓存。
- 测试：
  - 前后端 CRUD + 一键发起导航 e2e。
- 指标：
  - `favorites_sync_total{source="remote|local"}`
  - `quick_route_launch_total`
- 回滚：
  - 回退到本地存储路径（兼容已有 local 数据导入）。

## 3.7 PR-07 风险预警最小闭环（接近障碍 + 一键重算）

- 范围：
  - 先做 P1 能力的最小可用版，给 V2 打基础。
- API：
  - 复用 `GET /api/navigation/session/{id}/hazards`
  - 前端增加“预警 toast + 一键重算”。
- 前端状态：
  - `useNavigationSessionStore` 增加 warning 冷却时间和“已提醒 hazard 集”。
- 测试：
  - e2e：接近障碍提示 -> 点击重算 -> 路径更新。
- 指标：
  - `navigation_hazard_warning_total`
  - `navigation_reroutes_total{reason="OBSTACLE"}`

## 3.8 PR-08 监控与告警验收补齐（V1 完工门禁）

> 进度备注（2026-02-11）：`NavigationObstacleRerouteSpike` 与 `ObstacleDedupingDisabledUnexpectedly` 均已落地；并已补充 `obstacle_dedupe_enabled` 观测面板。

- 范围：
  - 把 V1 新增能力接到 dashboard + alert 规则。
- 监控：
  - Grafana 面板新增：
    - 偏好策略使用分布
    - 播报抑制（quiet hours）比率
    - 障碍去重比率
    - 快捷路线发起次数
- 告警：
  - `NavigationObstacleRerouteSpike` 阈值复核
  - 新增 `ObstacleDedupingDisabledUnexpectedly`（开关漂移）
- 测试：
  - PromQL 冒烟 + 告警规则 `promtool` 校验。

## 4. V2（P1）PR 清单（2-4 个月）

> 进度备注（2026-02-11）：PR-09 已落地“会话恢复（断网/刷新）+ waypoints 多段目标”基础能力：后端已支持 `resume_token` 恢复接口与 `waypoints` 持久化/推进，前端已接入本地快照、自动恢复与途经点交互。
>
> 进度备注（2026-02-11，补充）：PR-10/11/12/13 已完成最小闭环增强：
> - PR-10：会话响应补充楼层切换语义（`currentLevel/nextLevel/levelTransitionVia`），前端导航面板增加楼层切换提示；
> - PR-09 边界：偏航重算在多段会话中优先命中当前 leg 的下一个 waypoint；
> - PR-11/12：可信度评分与 SLA 升级链路已接入审核流并补充租户隔离强化；
> - PR-13：图版本对比与一键回滚接口已具备（`/api/admin/graph/snapshots/{id1}/diff/{id2}`、`/api/admin/graph/rollback/{snapshotId}`）。

- PR-09：多段目标（waypoints）+ 会话恢复（断网/刷新）  
- PR-10：室内外连续导航（楼层切换语义）  
- PR-11：审核可信度评分（位置精度/图片质量/历史信誉）  
- PR-12：审核 SLA 看板 + 超时自动催办/升级  
- PR-13：图变更审批流完善（版本对比、一键回滚指定版本）  

## 5. V3（P2）PR 清单（4-8 个月）

> 进度备注（2026-02-11）：V3 已有基础预埋（`tenant_id` 全链路字段与 `TenantFilter`、API Key 管理能力、experiment 表结构）。
>
> 进度备注（2026-02-11，补充）：V3 本轮完成最小闭环增强：
> - PR-14：多租户强隔离增强，覆盖 route 图加载/缓存键、障碍 dedupe/list/review、导航 resume-token、SLA 升级任务；
> - PR-15：开放 API 最小闭环上线（`POST /api/open/route`，API Key + scope 校验）；
> - PR-18：A/B 基础分群与曝光链路上线（`/api/experiments/{name}/assign` + `/exposure`），支持分群/曝光/转化埋点最小三件套。
>
> 进度备注（2026-02-11，收尾）：
> - PR-14：路由最近点查询（PostGIS KNN 与 bbox fallback）已补 tenant 过滤，避免跨租户吸附；
> - PR-15：Open API 链路已从 `permitAll` 收敛为 `authenticated`，ApiKey filter 注入认证上下文；scope 校验改为 token 化精确匹配（避免子串误判）。
>
> 进度备注（2026-02-11，新增）：
> - PR-16：应急联动最小闭环已落地：补齐 emergency 域 tenant 强隔离（event/contact/volunteer/response/broadcast），新增广播发布与历史接口（`/api/v1/emergency/broadcast*`），新增志愿者派单建议接口（`/api/v1/emergency/{eventId}/dispatch/suggest`），并接入审计日志。
>
> 进度备注（2026-02-11，新增）：
> - PR-17：可通行概率动态权重最小闭环已落地：新增租户级策略表 `t_route_passability_policy` 与管理接口（`GET/PUT /api/admin/route/weights`）；路由成本函数接入 `passabilityMinClamp + passabilityWeightFactor` 动态惩罚；`/api/route` 响应 `routingPolicy` 回传生效策略参数与来源（`DEFAULT/TENANT_POLICY`）。

- PR-14：多租户隔离（tenant_id 全链路）  
- PR-15：开放 API/SDK（限流、签名、审计）  
- PR-16：志愿者/安保联动与应急广播  
- PR-17：可通行概率模型与动态权重  
- PR-18：A/B 实验平台 + feature flag 分群投放  

## 6. 依赖关系（建议顺序）

1. PR-01 -> PR-02（偏好档案先于权重策略）  
2. PR-04 -> PR-05 -> PR-07（先提升障碍数据质量，再做预警闭环）  
3. PR-06 可与 PR-03 并行（前后端改动面独立）  
4. PR-08 最后收口（统一观测门禁）

## 7. 每个 PR 的 Definition of Done（统一门槛）

- 代码：后端单测 + PG IT + 前端单测 + 关键 e2e 通过
- 契约：OpenAPI 更新且 `frontend/src/api/schema.d.ts` 同步
- 运维：新增指标可在 Grafana 看板看到
- 文档：README/Runbook/变更说明补齐
- 回滚：提供“功能开关关闭 + 数据兼容”回退路径
