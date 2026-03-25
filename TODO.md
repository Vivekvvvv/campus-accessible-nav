# TODO

## 已完成

- [x] 项目初始化与基础框架搭建
- [x] 数据库设计（PostgreSQL + PostGIS + Flyway 迁移）
- [x] 后端 API 开发（26 个业务模块）
  - [x] 用户认证与鉴权（auth / JWT / Spring Security）
  - [x] 校园图数据管理（graph / building / node / edge）
  - [x] 路线规划与权重策略（route / spatial）
  - [x] 导航会话（navigation / WebSocket / waypoints / 偏航重算）
  - [x] 障碍上报与审核（obstacle / anomaly / 可信度评分 / SLA）
  - [x] 收藏与历史（favorites）
  - [x] 应急联动（emergency / 广播 / 志愿者派单）
  - [x] 用户无障碍偏好档案（profile）
  - [x] 语音设置（voice）
  - [x] 文件上传（file）
  - [x] A/B 实验（experiment）
  - [x] 多租户隔离（tenant）
  - [x] API Key 管理（apikey / 开放 API）
  - [x] 操作审计日志（audit）
  - [x] 管理后台（admin）
  - [x] 图版本对比与回滚（graph snapshot / diff / rollback）
  - [x] 可通行概率动态权重（route passability policy）
- [x] 前端页面开发
  - [x] 登录 / 注册页面
  - [x] 用户主页（地图 + 面板）
  - [x] 管理后台页面
  - [x] 搜索面板、路线面板、导航面板
  - [x] 设置面板、收藏面板、历史面板
  - [x] 障碍上报对话框、审核面板
  - [x] 质量面板、路线对比报告面板
  - [x] 全面 UI 重设计（2026-03-25）
- [x] 前端状态管理（11 个 Pinia Store + 单测）
- [x] 国际化（zh-CN / en-US 双语）
- [x] CI/CD 流水线配置（GitHub Actions）
  - [x] 后端构建与测试
  - [x] 前端 lint / 单测 / build / bundle budget
  - [x] OpenAPI 破坏性变更检查
  - [x] Flyway migration policy 检查
  - [x] Playwright E2E 测试
  - [x] Trivy / OSV 安全扫描
  - [x] Merge Gate 汇总检查
- [x] 监控系统集成
  - [x] Actuator + Micrometer 指标
  - [x] Prometheus + Grafana 仪表盘
  - [x] Alertmanager 告警规则
  - [x] Sentry 错误追踪
- [x] WebSocket 实时通知
- [x] MIT 许可证
- [x] 项目文档（README / 设计文档 / 配置矩阵 / 迁移策略）

## 待完善

- [ ] 后端测试覆盖补齐（admin / anomaly / apikey / emergency / experiment / file / messaging / tenant / voice 共 9 个模块零测试）
- [ ] 国际化 key 完全对齐（en-US 与 zh-CN 存在约 125 个 key 差异）
- [ ] PR-03 智能提醒策略收口（播报节流 / 夜间静音 / 震动配置）
- [ ] PR-06 收藏分组 + 常用路线一键发起
- [ ] PR-07 风险预警最小闭环（接近障碍 toast + 一键重算）
- [ ] Grafana 面板补齐新增业务指标
- [ ] SMS 通知接入预留（当前为 NoopSmsNotificationService）
