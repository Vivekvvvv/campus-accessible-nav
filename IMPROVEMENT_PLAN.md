# 校园无障碍导航系统 — 后续完善计划

> 更新时间：2026-03-25
> 项目整体完成度：约 90%（文档同步完成后）
> 排除范围：Docker Compose / 云端部署（项目不需要）

---

## 一、现状总结

### 已完成

| 模块 | 说明 |
|------|------|
| 后端核心 | 26 个业务模块全部实现（auth / graph / navigation / obstacle / route / favorites / emergency / experiment / tenant / voice 等） |
| 前端页面 | 4 个页面（Login / Register / User / Admin）+ 18 个组件 |
| 前端状态管理 | 11 个 Pinia Store，均有对应单测（17 个 .test.ts 文件） |
| E2E 测试 | Playwright 已配置，2 个 spec 文件（navigation.spec.js / ui.spec.js） |
| CI/CD | GitHub Actions 覆盖后端验证、前端 lint/单测/build、OpenAPI 破坏性变更检查、安全扫描、E2E |
| 可观测性 | Actuator + Micrometer + Prometheus + Grafana + Alertmanager + Sentry |
| 国际化 | zh-CN / en-US 双语（各 471 行），key 结构完全对齐 ✅ |
| 功能升级 | PR-09 ~ PR-18（V2/V3）已完成最小闭环 |
| UI 重设计 | 全部面板组件已完成重设计（2026-03-25） |
| 文档追踪 | TODO.md / DEV LOG.md / CSV 已同步至最新 ✅ |

### 存在的缺口

| 缺口 | 说明 |
|------|------|
| 后端测试覆盖不全 | 9 个模块零测试：admin / anomaly / apikey / emergency / experiment / file / messaging / tenant / voice |
| V1/P0 功能未完整收口 | PR-03（智能提醒策略）、PR-06（收藏分组）、PR-07（风险预警）尚未完成 |
| SMS 通知为空实现 | emergency 模块 `NoopSmsNotificationService` 仅日志输出 |

---

## 二、Git 提交规范

> **重要：每完成一个模块/任务就必须执行 git commit + push，禁止攒多个模块一起提交。**

### 提交流程

```
1. 完成一个模块的代码/文档变更
2. git add <相关文件>          ← 只添加本模块相关文件
3. git commit -m "<type>(<scope>): <subject>"
4. git push origin main
5. 确认推送成功后，再开始下一个模块
```

### 提交信息格式

遵循 Conventional Commits 规范：

```
<type>(<scope>): <subject>

- <动词开头的变更说明>
- <动词开头的变更说明>
```

### 类型映射

| type | 用途 | 示例 |
|------|------|------|
| `docs` | 文档变更 | `docs: update TODO and dev log` |
| `test` | 新增/修复测试 | `test(emergency): add controller integration tests` |
| `feat` | 新增功能 | `feat(voice): add quiet hours configuration` |
| `fix` | 缺陷修复 | `fix(i18n): align missing keys between zh-CN and en-US` |
| `chore` | 构建/工具/杂务 | `chore: update monitoring dashboards` |
| `refactor` | 重构 | `refactor(sms): add provider configuration switch` |

### 模块提交拆分原则

- **一个模块一次提交**：如后端测试分 9 个模块，每个模块完成后独立提交
- **文档与代码分开**：文档变更独立提交，不与功能代码混合
- **前后端分开**：同一功能的前后端变更分别提交（如 PR-03 先提交后端再提交前端）
- **可独立回退**：确保每个提交可独立回退，不破坏其他功能

---

## 三、完善任务清单

### P0 — 文档与追踪同步 ✅ 已完成

- [x] T1. 更新 TODO.md（已完成项打勾、补充真实待办）
- [x] T2. 补全 DEV LOG.md（2025-12-20 ~ 2026-03-25 完整时间线）
- [x] T3. 更新 CSV 追踪文件（Week 9~16、Gantt 全量任务）
- [x] 国际化 key 核查（经验证 zh-CN / en-US key 结构完全一致，无需修复）

**Git 记录：** `docs: update TODO, dev log, and tracking CSV files`

---

### P1 — 后端测试覆盖补齐（预计 3~5 天）

9 个零测试模块，按业务重要性排序。**每完成一个模块的测试就提交一次。**

| 优先级 | 模块 | 测试类型 | 关键测试点 | 提交信息 |
|--------|------|----------|-----------|---------|
| 1 | `emergency` | Controller IT + Service Unit | 应急事件 CRUD、广播发布、志愿者派单、tenant 隔离 | `test(emergency): add controller and service tests` |
| 2 | `apikey` | Controller IT | API Key 创建/撤销、scope 校验、filter 鉴权 | `test(apikey): add API key management tests` |
| 3 | `tenant` | Service Unit + IT | TenantContext 注入、TenantFilter 隔离 | `test(tenant): add tenant isolation tests` |
| 4 | `experiment` | Service Unit | 确定性分流、流量百分比、variant 解析、曝光记录 | `test(experiment): add assignment and exposure tests` |
| 5 | `anomaly` | Unit | ObstacleAnomalyDetector 检测阈值与边界 | `test(anomaly): add anomaly detection tests` |
| 6 | `admin` | Controller IT | AdminController 角色鉴权 | `test(admin): add admin controller tests` |
| 7 | `voice` | Controller IT | VoiceSettings CRUD、边界值校验 | `test(voice): add voice settings tests` |
| 8 | `file` | Controller IT | 上传/下载、类型/大小限制、权限校验 | `test(file): add file upload tests` |
| 9 | `messaging` | Unit | WebSocket 消息广播与订阅 | `test(messaging): add websocket messaging tests` |

**实施要点：**
- 复用已有 IT 基础设施：`PostgresITBase`（`backend/src/test/java/com/demo/accessiblenav/it/`）
- 参考已有模式：`ObstacleLoopPostgresIT`、`NavigationSessionControllerIntegrationTest`
- 目标：JaCoCo 行覆盖率提升至 60%+

---

### P2 — V1/P0 功能收口（预计 5~7 天）

对应 `docs/feature-upgrade-pr-plan.md` 中尚未完成的 V1 项。**前后端分别提交。**

#### T6. PR-03 智能提醒策略

| 步骤 | 内容 | 提交信息 |
|------|------|---------|
| 后端 | `VoiceSettingsEntity` 扩展字段 + Flyway 迁移 + API 扩展 | `feat(voice): add reminder policy fields and API` |
| 前端 | `SettingsPanel.vue` 播报策略 UI + `useNavigationSessionStore` 节流门控 | `feat(ui): add voice reminder policy settings` |
| 测试 | 前端节流逻辑单测 + 后端边界值校验 | `test(voice): add reminder policy tests` |

#### T7. PR-06 收藏分组 + 常用路线一键发起

| 步骤 | 内容 | 提交信息 |
|------|------|---------|
| 后端 | Flyway 迁移 + 分组/快捷路线 API | `feat(favorites): add groups and quick routes API` |
| 前端 | `FavoritesPanel.vue` 分组展示 + 一键发起 | `feat(ui): add favorites grouping and quick route launch` |
| 测试 | CRUD + E2E | `test(favorites): add group and quick route tests` |

#### T8. PR-07 风险预警最小闭环

| 步骤 | 内容 | 提交信息 |
|------|------|---------|
| 后端 | `GET /api/navigation/session/{id}/hazards` | `feat(navigation): add hazard warning API` |
| 前端 | hazards 轮询 + 预警 toast + 一键重算 | `feat(ui): add hazard warning and reroute button` |
| 测试 | E2E 接近障碍 → 重算 | `test(navigation): add hazard warning e2e tests` |

设计参考：`docs/design/nav-hazard-warning.md`

---

### P3 — 监控告警验收（预计 1~2 天）

#### T9. Grafana 面板补齐

- 检查 `monitoring/grafana/provisioning/dashboards/` 覆盖范围
- 补充缺失面板：偏好策略分布、播报抑制比率、快捷路线次数、障碍去重比率
- **提交信息：** `chore(monitoring): add missing Grafana dashboard panels`

#### T10. Alertmanager 规则复核

- 验证已有规则 + 补充 `EmergencyEventUnresolved30m`
- PromQL 冒烟 + `promtool` 校验
- **提交信息：** `chore(monitoring): add emergency alert rules`

---

### P4 — 代码质量收尾（预计 1~2 天）

#### T11. SMS 通知接入预留

- 补充配置化开关（`app.sms.provider=noop|aliyun|twilio`）
- 不接入真实服务，仅完善扩展点
- **提交信息：** `refactor(emergency): add SMS provider configuration switch`

#### T12. feature-upgrade-pr-plan.md 进度同步

- 补充所有 PR 的最终进度备注
- **提交信息：** `docs: sync feature upgrade PR plan progress`

---

## 四、执行顺序与时间线

```
Week 1:  P0 文档同步 ✅ → git commit → git push
Week 2:  P1 后端测试（emergency / apikey / tenant / experiment）→ 每个模块 git commit → git push
Week 3:  P1 后端测试（anomaly / admin / voice / file / messaging）→ 每个模块 git commit → git push
Week 4:  P2 T6 智能提醒策略（后端 → commit → 前端 → commit → 测试 → commit）→ git push
Week 5:  P2 T7 收藏分组（后端 → commit → 前端 → commit → 测试 → commit）→ git push
Week 6:  P2 T8 风险预警（后端 → commit → 前端 → commit → 测试 → commit）→ git push
Week 7:  P3 监控告警 → commit → P4 质量收尾 → commit → git push
```

总周期约 7 周，可根据实际情况压缩或并行。

---

## 五、验收标准

| 维度 | 标准 |
|------|------|
| 后端测试 | 所有 26 个模块均有测试覆盖，`mvn verify` 全绿，JaCoCo ≥ 60% |
| 前端测试 | `npm run test` 全绿，E2E 关键路径（登录→搜索→规划→导航→上报）通过 |
| 国际化 | zh-CN / en-US key 完全对齐 ✅，切换语言无 fallback 警告 |
| CI 门禁 | 所有 GitHub Actions check 绿灯 |
| 文档 | TODO.md 状态准确、DEV LOG 时间线连续、CSV 与实际进度一致 |
| 监控 | Grafana 面板覆盖所有业务指标，Alertmanager 规则通过 promtool 校验 |
| Git 规范 | 每个模块独立提交，提交信息符合 Conventional Commits，无超大合并提交 |

---

## 六、完成度预估

| 阶段 | 完成后整体完成度 |
|------|----------------|
| P0 文档同步 ✅ | ~90% |
| + P1 后端测试补齐 | ~93% |
| + P2 V1 功能收口 | ~96% |
| + P3 监控告警 | ~98% |
| + P4 质量收尾 | ~99% |
