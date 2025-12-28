import { chromium } from '@playwright/test'
import { Buffer } from 'node:buffer'

const BASE_URL = 'http://127.0.0.1:5175'

function base64Url(value) {
  return Buffer.from(value).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
}

function makeToken(expSecondsFromNow) {
  const header = { alg: 'HS256', typ: 'JWT' }
  const payload = {
    sub: 'manual-check',
    role: 'ADMIN',
    exp: Math.floor(Date.now() / 1000) + expSecondsFromNow,
  }
  return `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(payload))}.manual-signature`
}

async function createContext(browser, { token, reducedMotion = 'no-preference', force403 = false } = {}) {
  const context = await browser.newContext({ reducedMotion })

  await context.route('**/api/**', async (route) => {
    const url = route.request().url()
    const method = route.request().method().toUpperCase()

    if (force403 && url.includes('/api/profile/accessibility')) {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'TOKEN_EXPIRED', message: 'JWT expired' }),
      })
      return
    }

    if (url.includes('/api/profile/accessibility')) {
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            mobilityMode: 'WALK',
            avoidStairs: false,
            avoidSlope: false,
            avoidConstruction: true,
            maxSlopePercent: 12,
          }),
        })
        return
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ok: true }) })
      return
    }

    if (url.includes('/api/v1/voice-settings')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ voiceEnabled: true }) })
      return
    }

    if (url.includes('/api/favorites/places') || url.includes('/api/obstacles/reports') || url.includes('/api/route')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
      return
    }

    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })

  if (token) {
    await context.addInitScript(({ injectedToken }) => {
      localStorage.setItem('accessiblenav_admin_token', injectedToken)
      localStorage.setItem(
        'accessiblenav_admin_profile',
        JSON.stringify({ username: 'admin', role: 'ADMIN', creditScore: 80 })
      )
    }, { injectedToken: token })
  }

  const page = await context.newPage()
  return { context, page }
}

async function run() {
  const browser = await chromium.launch({ headless: true })
  const results = []

  try {
    // 1) 过期 token 自动清理并重定向登录
    {
      const { context, page } = await createContext(browser, { token: makeToken(-120) })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/admin`, { waitUntil: 'domcontentloaded' })
        await page.waitForURL(/\/login/, { timeout: 10000 })
        const state = await page.evaluate(() => ({
          adminToken: localStorage.getItem('accessiblenav_admin_token'),
          adminProfile: localStorage.getItem('accessiblenav_admin_profile'),
          pathname: location.pathname,
          search: location.search,
        }))
        passed =
          !state.adminToken &&
          !state.adminProfile &&
          state.pathname === '/login' &&
          (state.search.includes('redirect=%2Fadmin') || state.search.includes('redirect=/admin'))
        detail = JSON.stringify(state)
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'expired-token-redirect', passed, detail })
      await context.close()
    }

    // 2) 无效 token 在 403 后清理会话并重定向
    {
      const { context, page } = await createContext(browser, { token: makeToken(3600), force403: true })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/admin`, { waitUntil: 'domcontentloaded' })
        await page.waitForTimeout(1200)
        const state = await page.evaluate(() => ({
          adminToken: localStorage.getItem('accessiblenav_admin_token'),
          pathname: location.pathname,
          search: location.search,
        }))
        passed = !state.adminToken && state.pathname === '/login' && state.search.includes('redirect=/admin')
        detail = JSON.stringify(state)
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'invalid-token-api-clear', passed, detail })
      await context.close()
    }

    // 3) 倒计时显示且秒级变化（warn）
    {
      const { context, page } = await createContext(browser, { token: makeToken(240) })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
        const warning = page.locator('.session-warning')
        await warning.waitFor({ state: 'visible', timeout: 10000 })
        const className = (await warning.getAttribute('class')) || ''
        const text1 = (await warning.innerText()).trim()
        await page.waitForTimeout(2200)
        const text2 = (await warning.innerText()).trim()
        passed = className.includes('level-warn') && text1 !== text2
        detail = JSON.stringify({ className, text1, text2 })
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'warning-countdown-warn', passed, detail })
      await context.close()
    }

    // 4) 颜色分级 soft
    {
      const { context, page } = await createContext(browser, { token: makeToken(480) })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
        const warning = page.locator('.session-warning')
        await warning.waitFor({ state: 'visible', timeout: 10000 })
        const className = (await warning.getAttribute('class')) || ''
        passed = className.includes('level-soft')
        detail = JSON.stringify({ className })
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'warning-level-soft', passed, detail })
      await context.close()
    }

    // 5) 颜色分级 critical + 图标 + 动画 class
    {
      const { context, page } = await createContext(browser, { token: makeToken(50) })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
        const warning = page.locator('.session-warning')
        await warning.waitFor({ state: 'visible', timeout: 10000 })
        const className = (await warning.getAttribute('class')) || ''
        const iconVisible = await page.locator('.session-warning-action.critical-primary .session-warning-icon').isVisible()
        passed = className.includes('level-critical') && className.includes('pulse-critical') && iconVisible
        detail = JSON.stringify({ className, iconVisible })
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'warning-level-critical', passed, detail })
      await context.close()
    }

    // 6) 去登录按钮保留回跳参数
    {
      const { context, page } = await createContext(browser, { token: makeToken(240) })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/admin`, { waitUntil: 'domcontentloaded' })
        const loginBtn = page.locator('.session-warning-action').first()
        await loginBtn.waitFor({ state: 'visible', timeout: 10000 })
        await loginBtn.click()
        await page.waitForURL(/\/login/, { timeout: 10000 })
        const url = page.url()
        passed =
          url.includes('/login') &&
          url.includes('as=admin') &&
          (url.includes('redirect=%2Fadmin') || url.includes('redirect=/admin'))
        detail = url
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'warning-login-redirect', passed, detail })
      await context.close()
    }

    // 7) 稍后提醒：点击后短时间内不再显示
    {
      const { context, page } = await createContext(browser, { token: makeToken(240) })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
        const warning = page.locator('.session-warning')
        await warning.waitFor({ state: 'visible', timeout: 10000 })
        await page.locator('.session-warning-action.ghost').click()
        await warning.waitFor({ state: 'hidden', timeout: 3000 })
        await page.waitForTimeout(3000)
        const stillHidden = !(await warning.isVisible())
        passed = stillHidden
        detail = JSON.stringify({ stillHidden })
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'warning-snooze', passed, detail })
      await context.close()
    }

    // 8) reduced motion 时动画关闭
    {
      const { context, page } = await createContext(browser, {
        token: makeToken(50),
        reducedMotion: 'reduce',
      })
      let passed = false
      let detail = ''
      try {
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
        const warning = page.locator('.session-warning')
        await warning.waitFor({ state: 'visible', timeout: 10000 })

        const styles = await page.evaluate(() => {
          const warningEl = document.querySelector('.session-warning')
          const btnEl = document.querySelector('.session-warning-action.critical-primary-pulse')
          if (!warningEl || !btnEl) {
            return { warningAnimationName: null, buttonAnimationName: null }
          }
          const warningStyle = window.getComputedStyle(warningEl)
          const btnStyle = window.getComputedStyle(btnEl)
          return {
            warningAnimationName: warningStyle.animationName,
            buttonAnimationName: btnStyle.animationName,
          }
        })

        passed = styles.warningAnimationName === 'none' && styles.buttonAnimationName === 'none'
        detail = JSON.stringify(styles)
      } catch (error) {
        detail = String(error)
      }
      results.push({ id: 'warning-reduced-motion', passed, detail })
      await context.close()
    }
  } finally {
    await browser.close()
  }

  const passedCount = results.filter((item) => item.passed).length
  const failedCount = results.length - passedCount
  const summary = {
    total: results.length,
    passed: passedCount,
    failed: failedCount,
    results,
  }

  console.log(JSON.stringify(summary, null, 2))
  if (failedCount > 0) {
    process.exitCode = 1
  }
}

await run()
