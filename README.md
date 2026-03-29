# Campus Accessible Navigation

校园无障碍导航与障碍协同治理系统，面向广东白云学院江高校区场景构建。项目提供地图检索、无障碍路线规划、导航会话、障碍上报与审核、收藏与快捷路线、应急能力、图数据导入以及基础可观测性能力。

仓库采用前后端分离结构：

- `frontend/`：Vue 3 + Vite + Pinia 地图前端
- `backend/`：Spring Boot 3 + PostgreSQL/PostGIS 后端服务
- `data/`：校园图谱与原始地理数据
- `docs/`：设计、配置、发布与治理文档
- `monitoring/`：Prometheus / Grafana / Alertmanager 配置

## 功能概览

### 用户侧

- 校园 POI / 建筑搜索与起终点选择
- 步行 / 轮椅模式路线规划
- 导航会话创建、恢复、偏航自动重算
- 障碍 / 危险点提前预警与一键避障重算
- 障碍上报、我的上报记录
- 收藏地点、快捷路线、历史路线
- 语音导航、震动提醒、无障碍偏好配置
- 登录 / 注册 / 会话过期预警

### 管理侧

- 障碍审核与边通行性控制
- 图数据导入、图变更申请与审核流
- 管理后台鉴权与角色控制
- A/B 实验分流、多租户隔离
- 操作日志、质量面板、可观测性指标

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3、Vite、TypeScript、Pinia、Vue Router、Vue I18n、MapLibre GL |
| 后端 | Java 21、Spring Boot 3.2、Spring Security、Spring WebSocket、JPA、Flyway |
| 数据 | PostgreSQL、PostGIS、H2（测试/演示模式）、Redis（可选） |
| 测试 | Vitest、Playwright、JUnit、Spring Boot Test、JaCoCo |
| 可观测性 | Actuator、Micrometer、Prometheus、Grafana、Alertmanager |

## 项目亮点

- 路由不仅区分步行与轮椅模式，还支持可通行性权重、避障策略与无障碍偏好联动
- 导航会话支持持久化、恢复 token、偏航重算、危险点预警与客户端事件追踪
- 后端引入 PostGIS、KNN、缓存与图版本治理，适合继续做性能优化或空间能力扩展
- CI 已覆盖后端验证、前端 lint/单测/build、OpenAPI 破坏性变更检查、安全扫描与 E2E 关键链路

## 环境要求

- Node.js 20.19+ 或 22.12+
- npm 9+
- JDK 21+
- Maven 3.9+
- PostgreSQL 15+ 与 PostGIS 扩展

## 快速开始

### 1. 准备环境变量

复制根目录环境变量模板：

```powershell
Copy-Item ".env.example" ".env"
```

关键变量默认值已经适合本地开发，常用项包括：

- `SPRING_PROFILES_ACTIVE=dev`
- `BACKEND_PORT=8081`
- `FRONTEND_PORT=5173`
- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=accessible_nav`
- `DB_USER=postgres`
- `DB_PASSWORD=postgres`
- `JWT_SECRET=dev-secret-change-me-please-32-chars`

### 2. 准备数据库

项目默认使用 PostgreSQL + PostGIS，本地可先创建数据库：

```sql
CREATE DATABASE accessible_nav;
\c accessible_nav
CREATE EXTENSION IF NOT EXISTS postgis;
```

### 3. 一键启动前后端

仓库根目录提供了 PowerShell 启动脚本，会自动：

- 构建后端 JAR
- 注入本地运行参数
- 启动 Spring Boot 服务
- 启动 Vite 开发服务器

执行命令：

```powershell
.\start.ps1
```

常见变体：

```powershell
.\start.ps1 -BackendProfile "dev"
.\start.ps1 -BackendProfile "h2"
.\start.ps1 -FrontendPort 5174 -BackendPort 18081
```

停止服务：

```powershell
.\stop.ps1
```

### 4. 访问入口

- 前端：`http://localhost:5173`
- Swagger UI：`http://localhost:8081/swagger-ui/index.html`
- OpenAPI：`http://localhost:8081/v3/api-docs`
- Health：`http://localhost:8081/actuator/health`
- Prometheus 指标：`http://localhost:8081/actuator/prometheus`

## 单独运行子模块

### 前端

```powershell
Set-Location "frontend"
npm install
npm run dev
```

如果后端不在默认地址，可显式指定：

```powershell
$env:VITE_API_BASE_URL="http://localhost:18081"
npm.cmd run dev
```

### 后端

```powershell
Set-Location "backend"
mvn -s .mvn/settings.xml spring-boot:run
```

默认读取根目录 `.env` 或系统环境变量中的数据库、鉴权与运行配置。

## 测试与质量门禁

### 根目录快捷命令

```powershell
npm run frontend:lint
npm run frontend:typecheck
npm run frontend:test
npm run frontend:build
```

### 前端

```powershell
Set-Location "frontend"
npm ci
npm run lint
npm run test:coverage
npm run build
npm run test:e2e:critical
```

### 后端

```powershell
Set-Location "backend"
mvn -s .mvn/settings.xml clean verify -B
```

集成测试（需外部 PostgreSQL/PostGIS）：

```powershell
mvn -s .mvn/settings.xml -Pit verify
```

## OpenAPI 与前端类型生成

前端通过后端 OpenAPI 文档生成类型定义：

```powershell
Set-Location "frontend"
npm run api:gen
```

默认行为：

- 若本地 `http://localhost:8081/v3/api-docs` 可用，则直接生成
- 若后端未启动，脚本会尝试构建并临时启动后端后再生成

生成结果位于：

- `frontend/src/api/schema.d.ts`

## 配置与运行模式

当前仓库维护了多套 profile：

| Profile | 用途 | 数据源 |
|---|---|---|
| `dev` | 本地开发 | PostgreSQL + PostGIS |
| `h2` | 演示 / smoke | H2 |
| `test` | 单测与部分 CI | H2 |
| `it` | 后端集成测试 | PostgreSQL + PostGIS |
| `prod` | 生产环境 | PostgreSQL + PostGIS |

说明：

- `h2` 适合快速演示，但不等价于真实空间能力
- 生产环境默认关闭 Swagger / OpenAPI 暴露
- Redis、限流、日志格式、导航会话清理等能力都可通过环境变量调整

更多细节见：

- `docs/profile-config-matrix.md`
- `docs/env-frontend-auth.md`

## 目录结构

```text
campus-accessible-nav/
├─ backend/        Spring Boot 后端
├─ frontend/       Vue 地图前端
├─ data/           校园图谱、GeoJSON、导入数据
├─ docs/           设计文档、运行策略、升级计划
├─ monitoring/     Prometheus / Grafana / Alertmanager
├─ scripts/        发布、演练、性能、OpenAPI、Flyway 等脚本
├─ tools/          图数据转换与导入辅助工具
├─ start.ps1       本地一键启动
└─ stop.ps1        本地停止脚本
```

## 相关文档

- `backend/README.md`
- `frontend/README.md`
- `docs/design/nav-hazard-warning.md`
- `docs/openapi-contract-policy.md`
- `docs/flyway-migration-strategy.md`
- `docs/slo-canary-policy.md`
- `monitoring/grafana/provisioning/dashboards/`

## GitHub CI 覆盖范围

仓库 `.github/workflows/ci.yml` 当前包含以下门禁：

- **YAML 配置重复键检查**（yamllint，后端构建前置门禁）
- 后端构建与测试（依赖 YAML lint 通过后才运行）
- 前端 lint / 单测 / build / bundle budget
- OpenAPI breaking change 检查
- Flyway migration policy 检查
- Playwright E2E 关键路径与全量套件
- Trivy / OSV 安全扫描
- Merge Gate 汇总检查

## 适合继续扩展的方向

- 接入真实校园定位与蓝牙 / 室内定位能力
- 增加更细粒度的无障碍画像与个性化推荐
- 将图变更与障碍治理流程接入真实运维后台
- 补充 Docker Compose / 云端部署文档

## 许可证

当前仓库未声明开源许可证；如需公开发布到 GitHub，建议补充明确的 `LICENSE` 文件。  
哼，这种基础治理项别再漏了，笨蛋。
