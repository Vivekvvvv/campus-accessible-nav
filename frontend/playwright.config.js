import { defineConfig } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173'

export default defineConfig({
  testDir: './tests',
  timeout: 60000,
  expect: {
    timeout: 15000,
  },
  use: {
    baseURL,
    locale: 'zh-CN',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    permissions: ['clipboard-write', 'clipboard-read', 'geolocation'],
    geolocation: { longitude: 113.2025, latitude: 23.2750 },
  },
  webServer: [
    {
      command: 'mvn -q -f ../backend/pom.xml spring-boot:run',
      url: 'http://localhost:8081/api/graph/snapshot',
      reuseExistingServer: true,
      env: {
        ...process.env,
        SPRING_PROFILES_ACTIVE: process.env.SPRING_PROFILES_ACTIVE || 'h2',
        SERVER_PORT: process.env.SERVER_PORT || '8081',
        GRAPH_IMPORT_PATH: process.env.GRAPH_IMPORT_PATH || '../data/gbuc-jianggao/graph-import.json',
        APP_BOOTSTRAP_GRAPH_IMPORT_FORCE: process.env.APP_BOOTSTRAP_GRAPH_IMPORT_FORCE || 'true',
      },
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev -- --host',
      url: baseURL,
      reuseExistingServer: true,
      env: {
        ...process.env,
        VITE_API_BASE_URL: '',
        VITE_DISABLE_MAP: process.env.VITE_DISABLE_MAP || '1',
      },
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
})
