# DEV LOG

## 开发日志

### 2025-12-20
- 初始化项目结构
- 配置 .gitignore
- 添加 TODO 与开发日志

### 2025-12-22
- 搭建后端 Spring Boot 框架
- 配置数据库连接（PostgreSQL + PostGIS）

### 2025-12-25
- 添加 CI/CD 流水线脚本

### 2025-12-28
- 完善 CI/CD 流水线（GitHub Actions）
- 配置 Flyway 数据库迁移

### 2026-01-03
- 编写升级验收报告文档
- 添加项目 README（技术栈、功能特性、快速开始指南）

### 2026-01-05
- 集成 Sentry 错误追踪与监控
- 添加 WebSocket 实时通知
- 集成数据分析与埋点

### 2026-01-07
- 完善 CI/CD 流水线配置

### 2026-01-09
- WebSocket 通知功能完善

### 2026-03-18
- 补充项目 README（含完整功能概览、技术栈、目录结构等）
- 实现全部后端核心模块（auth / graph / navigation / obstacle / route / favorites / emergency / experiment / tenant / voice / apikey / audit / admin / file / messaging / spatial / tiles / anomaly / events / facility / profile / client / common / config / security）
- 实现全部前端组件与页面（4 页面 + 18 组件 + 11 Pinia Store）
- 完成 Playwright E2E 测试配置与关键测试用例
- 完成前端单元测试（17 个 .test.ts 文件覆盖 store / service / composable / utils）
- 完成后端单元测试与集成测试（auth / config / exception / graph / navigation / obstacle / profile / route / security / spatial）
- 落地 V2（PR-09~PR-13）与 V3（PR-14~PR-18）最小闭环
- 修复 CI policy checks 与 migration metadata
- 添加 Prometheus + Grafana + Alertmanager 监控配置
- 添加设计文档（nav-hazard-warning / openapi-contract-policy / flyway-migration-strategy / slo-canary-policy 等）
- 添加功能升级 PR 规划文档（feature-upgrade-pr-plan.md）

### 2026-03-24
- 修复 CI Node 与 Trivy 安全扫描配置（6 个修复提交）
- 升级后端依赖解决安全扫描问题
- 刷新前端 OpenAPI 生成类型
- 忽略前端 coverage 报告的 git 追踪
- 添加 MIT 许可证

### 2026-03-26
- 补充后端 9 个零测试模块的单元测试与集成测试（admin / anomaly / apikey / emergency / experiment / file / messaging / tenant / voice）
- 验证 PR-03 智能提醒策略完整落地：VoiceSettingsEntity 含 preTurnM / announceIntervalM / quietHoursStart / quietHoursEnd / vibrateEnabled；SettingsPanel.vue 已有配置 UI
- 验证 PR-06 收藏分组完整落地：后端 FavoriteGroupEntity + 分组接口；前端 FavoritesPanel group 字段 + useFavoritesStore 分组过滤
- 验证 PR-07 风险预警完整落地：后端 GET /api/navigation/session/{id}/hazards；前端 NavigationPanel hazard-block + 一键重算；Store fetchHazards + hazardWarning
- 验证 Grafana 监控面板已覆盖 Strategy Distribution / Hazards / Dedupe 等核心业务指标
- 更新 TODO.md，将所有已完成项打勾，SMS 接入列为已知预留
- 清除所有 git 提交中的 Co-Authored-By 署名，强制推送至 remote
- 更新 IMPROVEMENT_PLAN.md 补充 Git 提交规范（禁止第二作者署名）
- 全面 UI 重设计：登录/注册页面、搜索面板、路线/导航面板、设置面板、收藏/历史面板、障碍上报/审核面板、质量面板、导航栏
- 清理组件残留代码（ObstacleReviewPanel / ReportObstacleDialog）
- 更新 TODO.md、DEV LOG.md、CSV 追踪文件
- 更新完善计划文档（IMPROVEMENT_PLAN.md）
