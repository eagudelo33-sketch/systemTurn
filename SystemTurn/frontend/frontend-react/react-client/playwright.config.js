import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

// Servers are reused only on explicit opt-in; a stale backend would break the fresh-DB contract.
const reuse = process.env.E2E_REUSE === '1'
const backendDir = fileURLToPath(new URL('../../../backend/turnos-api', import.meta.url))
// `.\` is required on Windows: bare `mvnw.cmd` is not resolved from the cwd on some machines.
const mvnw = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw'

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `${mvnw} spring-boot:run`,
      cwd: backendDir,
      url: 'http://localhost:8080/actuator/health/readiness',
      timeout: 240_000,
      reuseExistingServer: reuse,
      env: {
        SPRING_PROFILES_ACTIVE: 'dev,demo',
        SPRING_DATASOURCE_URL: 'jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1',
      },
      stdout: process.env.CI ? 'pipe' : 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: reuse,
    },
  ],
})
