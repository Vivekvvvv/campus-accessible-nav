# frontend

## 本地开发

    npm install
    npm run dev

说明：
- 若后端在 `http://localhost:8081`，Vite 代理默认就能直连，不必额外设置环境变量。
- 若后端不在默认地址：
  - PowerShell: ``$env:VITE_API_BASE_URL="http://localhost:18081"; npm.cmd run dev``
  - Bash: `VITE_API_BASE_URL=http://localhost:18081 npm run dev`

仓库根目录也可以直接运行：

    .\start.ps1 -BackendProfile dev

## OpenAPI Types 生成

推荐直接执行：

    npm run api:gen

默认行为（无额外参数）：
- 若 `http://localhost:8081/v3/api-docs` 可用，直接生成。
- 若后端未启动，脚本会自动：构建 backend jar -> 临时启动 backend（profile=test）-> 生成 types -> 自动停止。

显式 URL 模式（用于远端/自定义 OpenAPI 地址）：

    OPENAPI_URL=http://localhost:8081/v3/api-docs npm run api:gen

PowerShell 示例：

    $env:OPENAPI_URL="http://localhost:8081/v3/api-docs"; npm.cmd run api:gen

说明：设置了 `OPENAPI_URL` 后，不会启用本地自动拉起后端兜底。

可选环境变量：
- `OPENAPI_AUTO_START_BACKEND`（默认 `1`）：设为 `0` 可关闭自动启动后端。
- `OPENAPI_BUILD_BACKEND`（默认 `1`）：设为 `0` 可跳过自动构建 backend jar。
- `OPENAPI_BACKEND_PROFILE`（默认 `test`）：自动启动后端时使用的 Spring profile。
- `OPENAPI_WAIT_SECONDS`（默认 `90`）：等待 `/v3/api-docs` 就绪的超时时间（秒）。

Windows 也可以在仓库根目录直接跑（会临时启动后端并生成 types）：

    .\\scripts\\gen-openapi-types.ps1
