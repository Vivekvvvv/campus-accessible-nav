# 前端认证环境变量说明

## 1. 适用范围

本说明用于前端会话与认证相关参数，覆盖以下能力：

- Token 提前过期裁剪
- 受保护路由守护轮询
- 会话到期预警展示
- 提示条“稍后提醒”时长
- Critical 动画开关

代码来源：`frontend/src/config/authConfig.ts`

## 2. 参数清单

| 变量名 | 类型 | 默认值 | 合法范围/值 | 说明 |
|---|---|---:|---|---|
| `VITE_AUTH_TOKEN_SKEW_SECONDS` | 整数 | `90` | `0` ~ `3600` | 将 Token 视为“提前过期”的秒数，避免边界时刻 401/403。 |
| `VITE_AUTH_GUARD_INTERVAL_MS` | 整数 | `15000` | `5000` ~ `300000` | 受保护路由会话守护轮询间隔（毫秒）。 |
| `VITE_AUTH_EXPIRY_WARNING_MINUTES` | 整数 | `10` | `1` ~ `180` | 距离到期多少分钟内开始显示顶部预警。 |
| `VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES` | 整数 | `5` | `1` ~ `120` | 点击“稍后提醒”后的静默时长（分钟）。 |
| `VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED` | 布尔字符串 | `true` | `1/0`、`true/false`、`yes/no`、`on/off` | 是否启用 `critical` 级脉冲动画。 |

> 说明：若配置不合法，系统会自动回退到默认值。

## 3. 推荐配置

### 3.1 开发环境（默认推荐）

```env
VITE_AUTH_TOKEN_SKEW_SECONDS=90
VITE_AUTH_GUARD_INTERVAL_MS=15000
VITE_AUTH_EXPIRY_WARNING_MINUTES=10
VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES=5
VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED=true
```

### 3.2 生产环境（稳健优先）

```env
VITE_AUTH_TOKEN_SKEW_SECONDS=120
VITE_AUTH_GUARD_INTERVAL_MS=20000
VITE_AUTH_EXPIRY_WARNING_MINUTES=15
VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES=5
VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED=true
```

### 3.3 低干扰模式（减少视觉提醒）

```env
VITE_AUTH_TOKEN_SKEW_SECONDS=90
VITE_AUTH_GUARD_INTERVAL_MS=30000
VITE_AUTH_EXPIRY_WARNING_MINUTES=8
VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES=10
VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED=false
```

## 4. 调优建议

- 频繁出现“刚点功能就要求登录”：适当增大 `VITE_AUTH_TOKEN_SKEW_SECONDS`。
- 后台停留恢复后才发现失效：适当减小 `VITE_AUTH_GUARD_INTERVAL_MS`（但不低于 `5000`）。
- 预警出现太早或太晚：调整 `VITE_AUTH_EXPIRY_WARNING_MINUTES`。
- 用户觉得提示太频繁：增大 `VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES`。
- 用户对动效敏感：设为 `VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED=false`。

## 5. 验证清单

- 修改 `.env` 后重启前端，确认新值生效。
- 检查受保护路由失效后是否能自动跳登录。
- 检查预警条倒计时、分级色彩、按钮交互是否符合配置预期。
- 在系统“减少动态效果”开启时确认动画自动关闭。

