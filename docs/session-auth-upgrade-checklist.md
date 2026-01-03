# 会话与认证稳定性升级清单（前端）

## 1. 文档目的

用于沉淀本轮“过期 Token 导致 `Forbidden` 体验不佳”相关升级内容，作为后续回归、发布验收、运维排障的统一检查单。

相关配置说明：`docs/env-frontend-auth.md`

## 2. 升级范围

- 认证会话治理（Token 解析、提前过期、统一清理）
- API 请求链路（`apiService` / `fetchUtils`）
- 路由守卫与会话状态同步
- UI 预警（倒计时、去登录、稍后提醒、分级颜色、critical 动画）
- 环境变量配置与可运维化

## 3. 已完成项（功能清单）

### 3.1 会话工具层

- [x] 新增统一会话工具：`frontend/src/utils/authSession.ts`
- [x] 支持 JWT `exp` 解析与过期判断
- [x] 支持统一清理 `admin/user/all` 会话
- [x] 支持会话变更事件：`accessiblenav-session-changed`
- [x] 支持提前失效裁剪（`VITE_AUTH_TOKEN_SKEW_SECONDS`，默认 `90`）
- [x] 支持获取剩余有效时长（毫秒）

### 3.2 API 链路治理

- [x] `apiService` 改为从统一会话工具读取有效 Token
- [x] `apiService` 在 `401/403` 且命中过期/认证语义时自动清理会话
- [x] `apiService` Token 刷新时按 `admin/user` 源正确回写
- [x] `fetchUtils` 同步接入统一 Token 读取与错误清理策略
- [x] `fetchUtils` 与 `apiService` 的会话行为保持一致

### 3.3 路由与会话一致性

- [x] 路由守卫接入有效 Token 判定，过期后直接引导登录
- [x] Session Store 在刷新时先裁剪过期 Token
- [x] `App.vue` 退出登录改为统一会话清理函数

### 3.4 主动守护（防后台停留失效）

- [x] 增加受保护路由定时守护（默认 `15s`）
- [x] 在 `focus`、`visibilitychange`、路由切换时主动复检会话
- [x] 会话失效后自动跳登录并携带 `redirect`

### 3.5 会话到期预警 UI

- [x] 顶部新增会话即将过期提示条
- [x] 显示实时倒计时（分/秒，1s 刷新）
- [x] 提供“去登录”动作（带回跳）
- [x] 提供“稍后提醒”动作（默认 `5` 分钟）
- [x] 提示等级分级：
  - [x] `soft`（>5 分钟）
  - [x] `warn`（<=5 分钟）
  - [x] `critical`（<=1 分钟）
- [x] `critical` 级支持提示条轻微脉冲动画（可开关）
- [x] `critical` 级“去登录”按钮高亮节奏 + `!` 图标
- [x] 对 `prefers-reduced-motion: reduce` 提供动画降级

### 3.6 国际化与配置

- [x] 中英文文案已补齐并通过键一致性校验
- [x] 新增统一配置模块：`frontend/src/config/authConfig.ts`
- [x] 新增配置项：
  - [x] `VITE_AUTH_GUARD_INTERVAL_MS`
  - [x] `VITE_AUTH_TOKEN_SKEW_SECONDS`
  - [x] `VITE_AUTH_EXPIRY_WARNING_MINUTES`
  - [x] `VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES`
  - [x] `VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED`

## 4. 关键文件索引

- `frontend/src/utils/authSession.ts`
- `frontend/src/services/apiService.ts`
- `frontend/src/utils/fetchUtils.ts`
- `frontend/src/router/index.ts`
- `frontend/src/stores/useSessionStore.ts`
- `frontend/src/App.vue`
- `frontend/src/config/authConfig.ts`
- `frontend/src/locales/zh-CN.js`
- `frontend/src/locales/en-US.js`
- `.env`
- `.env.example`

## 5. 验收清单（发布前必勾）

### 5.1 自动化校验

- [x] 在 `frontend/` 执行 `npm run typecheck` 通过
- [x] 在 `frontend/` 执行 `npm run i18n:check` 通过
- [x] 在 `frontend/` 执行 `npm run test:unit` 通过
- [x] 在 `frontend/` 执行 `npm run lint` 无新增 error（warning 需评估）

### 5.2 手工场景验收

- [x] 本地写入过期 Token 后刷新页面，能自动清理并引导登录
- [x] 访问受保护路由时，失效会话不会停留在当前页
- [x] 到期前提示条按预期出现，倒计时每秒更新
- [x] 点击“去登录”后能进入登录页并保留回跳地址
- [x] 点击“稍后提醒”后，在配置时长内不重复提示
- [x] 剩余时间跨越阈值时，颜色从黄→橙→红切换正确
- [x] `critical` 级动画可见，且在减少动态效果偏好下自动关闭

> 备注（2026-02-11）：手工场景已通过 `frontend/tools/manual-session-acceptance.mjs` 自动执行并记录，结果文件：`.run/manual-session-acceptance-result.json`。

## 6. 建议回归顺序

1. 先跑自动化（类型、i18n、单测、lint）
2. 再做会话失效链路（API + 路由）
3. 最后做 UI 预警与交互（倒计时、动作、动画）

## 7. 回滚策略（最小影响）

- UI 异常优先回退 `App.vue` 的预警 UI 与动画相关提交
- 若业务受阻，临时将 `VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED=false`
- 若提示过于敏感，可调大：
  - `VITE_AUTH_TOKEN_SKEW_SECONDS`
  - `VITE_AUTH_EXPIRY_WARNING_MINUTES`
- 回滚后必须重跑第 5 章验收项
