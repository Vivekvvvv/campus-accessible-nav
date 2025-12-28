import { test, expect } from '@playwright/test'
import { attachPageDebug, installE2ENoiseInterceptors } from './helpers/e2eNoiseControl.js'

test.beforeEach(async ({ page }) => {
  await installE2ENoiseInterceptors(page)
})

async function openDetails(detailsLocator) {
  await detailsLocator.evaluate((el) => {
    el.open = true
  })
}

async function setSearchPoint(page, query, action) {
  const searchSection = page.getByTestId('panel-search')
  await openDetails(searchSection)

  const input = searchSection.getByTestId('search-input')
  await input.fill(query)
  await input.press('Enter')

  const resultCard = searchSection.getByTestId('search-result-card').filter({ hasText: query }).first()
  await expect(resultCard).toBeVisible()

  const actionTestId = action === 'start' ? 'search-result-set-start' : 'search-result-set-end'
  await resultCard.getByTestId(actionTestId).click()
}

async function loginViaApiAndSeedSession(page, request, userPrefix) {
  const suffix = `${Date.now().toString(36)}${Math.floor(Math.random() * 1000).toString(36)}`
  const base = String(userPrefix || 'e2e').replace(/[^a-zA-Z0-9_]/g, '').slice(0, 20)
  const username = `${base}_${suffix}`.slice(0, 32)
  const password = 'Abx9Kq7M'

  const registerResp = await request.post('/api/auth/register', {
    data: { username, password },
  })
  if (![200, 400].includes(registerResp.status())) {
    throw new Error(`register failed: ${registerResp.status()}`)
  }

  const loginResp = await request.post('/api/auth/login', {
    data: { username, password },
  })
  expect(loginResp.status()).toBe(200)
  const loginData = await loginResp.json()

  await page.addInitScript((data) => {
    localStorage.setItem('accessiblenav_user_token', data.token)
    localStorage.setItem(
      'accessiblenav_user_profile',
      JSON.stringify({
        username: data.username,
        role: data.role,
        creditScore: data.creditScore ?? 0,
      })
    )
    window.dispatchEvent(new Event('accessiblenav-session-changed'))
  }, loginData)
}

async function prepareRouteAndNavPanel(page) {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('panel-search')).toBeVisible({ timeout: 20000 })

  await setSearchPoint(page, '\u6559\u5b66\u697c', 'start')
  await setSearchPoint(page, '\u56fe\u4e66\u9986\u5165\u53e3', 'end')

  const navPanel = page.getByTestId('panel-navigation')
  await openDetails(navPanel)

  const startBtn = navPanel.getByTestId('nav-start-session')
  await expect(startBtn).toBeVisible()
  await expect(startBtn).toBeEnabled({ timeout: 30000 })

  return navPanel
}

async function updateAccessibilityProfile(page, profile = {}) {
  const settingsPanel = page.getByTestId('panel-settings')
  await openDetails(settingsPanel)

  const desiredMode = profile.mobilityMode || 'WHEELCHAIR'
  const desiredAvoidStairs = profile.avoidStairs ?? true
  const desiredAvoidSlope = profile.avoidSlope ?? true
  const desiredAvoidConstruction = profile.avoidConstruction ?? true
  const desiredMaxSlope = String(profile.maxSlopePercent ?? 8)

  await settingsPanel.getByTestId('accessibility-mobility-mode').selectOption(desiredMode)

  const avoidStairs = settingsPanel.getByTestId('accessibility-avoid-stairs')
  if ((await avoidStairs.isChecked()) !== desiredAvoidStairs) {
    await avoidStairs.click()
  }

  const avoidSlope = settingsPanel.getByTestId('accessibility-avoid-slope')
  if ((await avoidSlope.isChecked()) !== desiredAvoidSlope) {
    await avoidSlope.click()
  }

  const avoidConstruction = settingsPanel.getByTestId('accessibility-avoid-construction')
  if ((await avoidConstruction.isChecked()) !== desiredAvoidConstruction) {
    await avoidConstruction.click()
  }

  const maxSlopeInput = settingsPanel.getByTestId('accessibility-max-slope')
  await maxSlopeInput.fill(desiredMaxSlope)

  const waitSave = page.waitForResponse((resp) => {
    return resp.url().includes('/api/profile/accessibility')
      && resp.request().method() === 'PUT'
      && resp.status() === 200
  })
  await settingsPanel.getByTestId('accessibility-save').click()
  await waitSave
}

test.describe('Navigation E2E', () => {
  test('navigation session can start and end', async ({ page, request, context }) => {
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_start_end')
    const navPanel = await prepareRouteAndNavPanel(page)

    await context.setGeolocation({ longitude: 113.2025, latitude: 23.2750 })
    await navPanel.getByTestId('nav-locating-toggle').click()

    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    await navPanel.getByTestId('nav-end-reason').selectOption('USER_END')
    await navPanel.getByTestId('nav-end-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ENDED', { timeout: 20000 })
  })

  test('off-route triggers automatic reroute (reason=DEVIATION)', async ({ page, request, context }) => {
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_deviation')

    const rerouteReasons = []
    await page.route('**/api/navigation/session/*/reroute', async (route) => {
      try {
        const body = route.request().postDataJSON()
        rerouteReasons.push(String(body?.reason || ''))
      } catch {
        rerouteReasons.push('UNKNOWN')
      }
      await route.continue()
    })

    const navPanel = await prepareRouteAndNavPanel(page)
    await context.setGeolocation({ longitude: 113.2025, latitude: 23.2750 })
    await navPanel.getByTestId('nav-locating-toggle').click()
    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    // Push several clearly off-route locations to trigger auto reroute.
    await context.setGeolocation({ longitude: 113.2080, latitude: 23.2810 })
    await page.waitForTimeout(1300)
    await context.setGeolocation({ longitude: 113.2085, latitude: 23.2815 })
    await page.waitForTimeout(1300)
    await context.setGeolocation({ longitude: 113.2090, latitude: 23.2820 })

    await expect.poll(() => rerouteReasons.includes('DEVIATION'), { timeout: 30000 }).toBeTruthy()
  })

  test('hazard warning supports one-click reroute (reason=OBSTACLE)', async ({ page, request, context }) => {
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_hazard')

    await page.route('**/api/navigation/session/*/hazards**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            effectId: 10001,
            edgeId: 20001,
            reason: 'construction',
            endAt: null,
            fromLat: 23.2750,
            fromLng: 113.2020,
            toLat: 23.2751,
            toLng: 113.2021,
            distanceToRouteM: 2,
            routeAtM: 5,
          },
        ]),
      })
    })

    const rerouteReasons = []
    await page.route('**/api/navigation/session/*/reroute', async (route) => {
      try {
        const body = route.request().postDataJSON()
        rerouteReasons.push(String(body?.reason || ''))
      } catch {
        rerouteReasons.push('UNKNOWN')
      }
      await route.continue()
    })

    const navPanel = await prepareRouteAndNavPanel(page)

    await context.setGeolocation({ longitude: 113.200776, latitude: 23.275784 })
    await navPanel.getByTestId('nav-locating-toggle').click()
    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    // Force one more location update tick so hazard check runs after session starts.
    await context.setGeolocation({ longitude: 113.200790, latitude: 23.275790 })

    const hazardRerouteBtn = navPanel.getByTestId('nav-hazard-reroute')
    await expect(hazardRerouteBtn).toBeVisible({ timeout: 30000 })
    await expect(hazardRerouteBtn).toBeEnabled()
    await hazardRerouteBtn.click()

    await expect.poll(() => rerouteReasons.includes('OBSTACLE'), { timeout: 20000 }).toBeTruthy()
  })

  test.describe('Navigation Hazard Edge Cases', () => {

  test('hazard warning deduplicates same effectId', async ({ page, request, context }) => {
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_hazard_dedupe')

    const hazardReason = 'stairs_blocked'
    await page.route('**/api/navigation/session/*/hazards**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            effectId: 10002,
            edgeId: 20002,
            reason: hazardReason,
            endAt: null,
            fromLat: 23.2750,
            fromLng: 113.2020,
            toLat: 23.2751,
            toLng: 113.2021,
            distanceToRouteM: 2,
            routeAtM: 5,
          },
        ]),
      })
    })

    const hazardWarnEvents = []
    await page.route('**/api/navigation/session/*/client-event', async (route) => {
      try {
        const body = route.request().postDataJSON()
        if (String(body?.type || '') === 'HAZARD_WARNED') {
          hazardWarnEvents.push(String(body?.payload || ''))
        }
      } catch {
        // ignore parse errors
      }
      await route.fulfill({
        status: 204,
        contentType: 'application/json',
        body: '',
      })
    })

    const navPanel = await prepareRouteAndNavPanel(page)
    await context.setGeolocation({ longitude: 113.200776, latitude: 23.275784 })
    await navPanel.getByTestId('nav-locating-toggle').click()
    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    await context.setGeolocation({ longitude: 113.200790, latitude: 23.275790 })
    const hazardRerouteBtn = navPanel.getByTestId('nav-hazard-reroute')
    await expect(hazardRerouteBtn).toBeVisible({ timeout: 30000 })

    // Keep feeding location updates; same effectId should only warn once.
    await page.waitForTimeout(1800)
    await context.setGeolocation({ longitude: 113.200792, latitude: 23.275792 })
    await page.waitForTimeout(1800)
    await context.setGeolocation({ longitude: 113.200794, latitude: 23.275794 })
    await page.waitForTimeout(1800)

    await expect.poll(() => hazardWarnEvents.length, { timeout: 20000 }).toBe(1)
    await expect.poll(() => hazardWarnEvents.filter((p) => p === hazardReason).length, { timeout: 20000 }).toBe(1)
  })

  test('hazard warning respects cooldown for different effectIds', async ({ page, request, context }) => {
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_hazard_cooldown')

    let hazardCall = 0
    await page.route('**/api/navigation/session/*/hazards**', async (route) => {
      hazardCall += 1
      const effectId = hazardCall <= 1 ? 11001 : 11002
      const reason = hazardCall <= 1 ? 'construction' : 'road_closure'
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            effectId,
            edgeId: 21000 + effectId,
            reason,
            endAt: null,
            fromLat: 23.2750,
            fromLng: 113.2020,
            toLat: 23.2751,
            toLng: 113.2021,
            distanceToRouteM: 2,
            routeAtM: 5,
          },
        ]),
      })
    })

    const hazardWarnEvents = []
    await page.route('**/api/navigation/session/*/client-event', async (route) => {
      try {
        const body = route.request().postDataJSON()
        if (String(body?.type || '') === 'HAZARD_WARNED') {
          hazardWarnEvents.push(String(body?.payload || ''))
        }
      } catch {
        // ignore parse errors
      }
      await route.fulfill({
        status: 204,
        contentType: 'application/json',
        body: '',
      })
    })

    const navPanel = await prepareRouteAndNavPanel(page)
    await context.setGeolocation({ longitude: 113.200776, latitude: 23.275784 })
    await navPanel.getByTestId('nav-locating-toggle').click()
    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    await context.setGeolocation({ longitude: 113.200790, latitude: 23.275790 })
    await expect(navPanel.getByTestId('nav-hazard-reroute')).toBeVisible({ timeout: 30000 })

    // Wait past hazard fetch interval so the second effectId is returned,
    // but still within cooldown window; second warning should be suppressed.
    await page.waitForTimeout(16000)
    await context.setGeolocation({ longitude: 113.200795, latitude: 23.275795 })
    await page.waitForTimeout(2000)

    await expect.poll(() => hazardWarnEvents.length, { timeout: 20000 }).toBe(1)
    await expect.poll(() => hazardWarnEvents[0] || '', { timeout: 20000 }).toContain('construction')
  })

  test('hazard + deviation reroute concurrency keeps session stable', async ({ page, request, context }) => {
    test.setTimeout(120000)
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_hazard_concurrency')

    let deviationObserved = false
    await page.route('**/api/navigation/session/*/hazards**', async (route) => {
      const effectId = deviationObserved ? 12002 : 12001
      const reason = deviationObserved ? 'flooded_path' : 'construction'
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            effectId,
            edgeId: 22000 + effectId,
            reason,
            endAt: null,
            fromLat: 23.2750,
            fromLng: 113.2020,
            toLat: 23.2751,
            toLng: 113.2021,
            distanceToRouteM: 2,
            routeAtM: 5,
          },
        ]),
      })
    })

    const rerouteReasons = []
    await page.route('**/api/navigation/session/*/reroute', async (route) => {
      try {
        const body = route.request().postDataJSON()
        const reason = String(body?.reason || 'UNKNOWN')
        rerouteReasons.push(reason)
        if (reason === 'DEVIATION') {
          deviationObserved = true
        }
      } catch {
        rerouteReasons.push('UNKNOWN')
      }
      await route.fulfill({
        status: 204,
        contentType: 'application/json',
        body: '',
      })
    })

    const navPanel = await prepareRouteAndNavPanel(page)
    await context.setGeolocation({ longitude: 113.200776, latitude: 23.275784 })
    await navPanel.getByTestId('nav-locating-toggle').click()
    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    // First hazard appears on-route.
    await context.setGeolocation({ longitude: 113.200790, latitude: 23.275790 })
    const hazardRerouteBtn = navPanel.getByTestId('nav-hazard-reroute')
    await expect(hazardRerouteBtn).toBeVisible({ timeout: 30000 })

    // Trigger automatic off-route reroute while hazard warning state exists.
    await context.setGeolocation({ longitude: 113.2080, latitude: 23.2810 })
    await page.waitForTimeout(1300)
    await context.setGeolocation({ longitude: 113.2085, latitude: 23.2815 })
    await page.waitForTimeout(1300)
    await context.setGeolocation({ longitude: 113.2090, latitude: 23.2820 })

    await expect.poll(() => rerouteReasons.filter((r) => r === 'DEVIATION').length, { timeout: 30000 }).toBe(1)
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    // Wait past hazard cooldown + fetch interval, then ensure hazard flow still works.
    await page.waitForTimeout(21000)
    await context.setGeolocation({ longitude: 113.200792, latitude: 23.275792 })
    await expect(hazardRerouteBtn).toBeVisible({ timeout: 30000 })
    await hazardRerouteBtn.click()

    await expect.poll(() => rerouteReasons.filter((r) => r === 'OBSTACLE').length, { timeout: 30000 }).toBe(1)
    await expect.poll(() => rerouteReasons.filter((r) => r === 'DEVIATION').length, { timeout: 10000 }).toBe(1)
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })
  })

  test('session restore keeps hazard state stable after refresh', async ({ page, request, context }) => {
    test.setTimeout(120000)
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_restore_hazard')

    await page.route('**/api/navigation/session/*/hazards**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            effectId: 13001,
            edgeId: 23001,
            reason: 'construction',
            endAt: null,
            fromLat: 23.2750,
            fromLng: 113.2020,
            toLat: 23.2751,
            toLng: 113.2021,
            distanceToRouteM: 2,
            routeAtM: 5,
          },
        ]),
      })
    })

    let resumeFetchCalls = 0
    await page.route('**/api/navigation/session/resume-token/*', async (route) => {
      resumeFetchCalls += 1
      await route.continue()
    })

    const hazardWarnEvents = []
    await page.route('**/api/navigation/session/*/client-event', async (route) => {
      try {
        const body = route.request().postDataJSON()
        if (String(body?.type || '') === 'HAZARD_WARNED') {
          hazardWarnEvents.push(String(body?.payload || ''))
        }
      } catch {
        // ignore parse errors
      }
      await route.fulfill({
        status: 204,
        contentType: 'application/json',
        body: '',
      })
    })

    let navPanel = await prepareRouteAndNavPanel(page)
    await context.setGeolocation({ longitude: 113.200776, latitude: 23.275784 })
    await navPanel.getByTestId('nav-locating-toggle').click()
    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    // Trigger one hazard warning before refresh.
    await context.setGeolocation({ longitude: 113.200790, latitude: 23.275790 })
    await expect(navPanel.getByTestId('nav-hazard-reroute')).toBeVisible({ timeout: 30000 })
    await expect.poll(() => hazardWarnEvents.length, { timeout: 20000 }).toBe(1)
    const beforeRefreshWarns = hazardWarnEvents.length

    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.getByTestId('panel-search')).toBeVisible({ timeout: 20000 })

    navPanel = page.getByTestId('panel-navigation')
    await openDetails(navPanel)

    // Session should auto-restore via resume token, and no immediate false hazard warning should fire.
    await expect.poll(() => resumeFetchCalls, { timeout: 30000 }).toBeGreaterThan(0)
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 30000 })
    await page.waitForTimeout(1500)
    await expect.poll(() => hazardWarnEvents.length, { timeout: 10000 }).toBe(beforeRefreshWarns)

    // After refresh, same effect should still warn at most once under repeated location updates.
    const locatingBtn = navPanel.getByTestId('nav-locating-toggle')
    const locatingActive = await locatingBtn.evaluate((el) => el.classList.contains('active'))
    if (!locatingActive) {
      await locatingBtn.click()
    }

    await context.setGeolocation({ longitude: 113.200792, latitude: 23.275792 })
    await page.waitForTimeout(1800)
    await context.setGeolocation({ longitude: 113.200794, latitude: 23.275794 })
    await page.waitForTimeout(1800)
    await context.setGeolocation({ longitude: 113.200796, latitude: 23.275796 })
    await page.waitForTimeout(1800)

    await expect.poll(() => hazardWarnEvents.length, { timeout: 30000 }).toBe(beforeRefreshWarns + 1)
    await expect.poll(() => hazardWarnEvents.filter((p) => p === 'construction').length, { timeout: 30000 }).toBe(2)
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })
  })

  test('waypoints leg switch keeps hazard distance trigger correct', async ({ page, request, context }) => {
    test.setTimeout(120000)
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_nav_waypoint_hazard')

    let currentLeg = 0
    await page.route('**/api/navigation/session', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sessionId: 'mock-waypoint-session',
          status: 'ACTIVE',
          mode: 'WALK',
          destinationLat: 23.2762,
          destinationLng: 113.2016,
          destinationName: '图书馆入口',
          deviationCount: 0,
          rerouteCount: 0,
          resumeToken: 'mock-waypoint-token',
          currentLeg: 0,
          totalLegs: 2,
          waypoints: [
            { lat: 23.27589, lng: 113.20089, name: 'W1', reached: false },
          ],
          route: {
            mode: 'WALK',
            distanceM: 120,
            durationSec: 90,
            riskCount: 0,
            path: [
              { lng: 113.20079, lat: 23.27579 },
              { lng: 113.20089, lat: 23.27589 },
            ],
          },
        }),
      })
    })

    await page.route('**/api/navigation/session/*/advance-leg', async (route) => {
      currentLeg = 1
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sessionId: 'mock-waypoint-session',
          status: 'ACTIVE',
          mode: 'WALK',
          destinationLat: 23.2762,
          destinationLng: 113.2016,
          destinationName: '图书馆入口',
          deviationCount: 0,
          rerouteCount: 0,
          resumeToken: 'mock-waypoint-token',
          currentLeg: 1,
          totalLegs: 2,
          waypoints: [
            { lat: 23.27589, lng: 113.20089, name: 'W1', reached: true },
          ],
          route: {
            mode: 'WALK',
            distanceM: 140,
            durationSec: 100,
            riskCount: 0,
            path: [
              { lng: 113.20150, lat: 23.27610 },
              { lng: 113.20160, lat: 23.27620 },
            ],
          },
        }),
      })
    })

    await page.route('**/api/navigation/session/*/location', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ok: true }),
      })
    })

    await page.route('**/api/navigation/session/*/hazards**', async (route) => {
      const payload = currentLeg === 0
        ? [
            {
              effectId: 14001,
              edgeId: 24001,
              reason: 'leg1_hazard',
              endAt: null,
              fromLat: 23.2758,
              fromLng: 113.2008,
              toLat: 23.2759,
              toLng: 113.2009,
              distanceToRouteM: 2,
              routeAtM: 8,
            },
          ]
        : [
            {
              effectId: 14002,
              edgeId: 24002,
              reason: 'leg2_hazard',
              endAt: null,
              fromLat: 23.2761,
              fromLng: 113.2015,
              toLat: 23.2762,
              toLng: 113.2016,
              distanceToRouteM: 2,
              routeAtM: 8,
            },
          ]

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(payload),
      })
    })

    const hazardWarnEvents = []
    await page.route('**/api/navigation/session/*/client-event', async (route) => {
      try {
        const body = route.request().postDataJSON()
        if (String(body?.type || '') === 'HAZARD_WARNED') {
          hazardWarnEvents.push(String(body?.payload || ''))
        }
      } catch {
        // ignore parse errors
      }
      await route.fulfill({
        status: 204,
        contentType: 'application/json',
        body: '',
      })
    })

    const navPanel = await prepareRouteAndNavPanel(page)
    await context.setGeolocation({ longitude: 113.20079, latitude: 23.27579 })
    await navPanel.getByTestId('nav-locating-toggle').click()
    await navPanel.getByTestId('nav-start-session').click()
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })

    await context.setGeolocation({ longitude: 113.20080, latitude: 23.27580 })
    await expect(navPanel.getByTestId('nav-hazard-reroute')).toBeVisible({ timeout: 30000 })
    await expect.poll(() => hazardWarnEvents.filter((p) => p === 'leg1_hazard').length, { timeout: 30000 }).toBe(1)

    const advanceLegBtn = navPanel.getByTestId('nav-advance-leg')
    await expect(advanceLegBtn).toBeVisible({ timeout: 30000 })
    await advanceLegBtn.click()

    // Wait past hazard fetch interval (15s) and warning cooldown (20s), then trigger location update near leg-2 route.
    await page.waitForTimeout(21000)
    await context.setGeolocation({ longitude: 113.20151, latitude: 23.27611 })
    await expect(navPanel.getByTestId('nav-hazard-reroute')).toBeVisible({ timeout: 30000 })
    await expect.poll(() => hazardWarnEvents.filter((p) => p === 'leg2_hazard').length, { timeout: 30000 }).toBe(1)
    await expect.poll(() => hazardWarnEvents.length, { timeout: 30000 }).toBe(2)
    await expect(navPanel.getByTestId('nav-session-status')).toContainText('ACTIVE', { timeout: 20000 })
  })

  })

  test('updating accessibility profile should affect route policy selection', async ({ page, request }) => {
    attachPageDebug(page)
    await loginViaApiAndSeedSession(page, request, 'e2e_profile_route_policy')

    const routingPolicies = []
    page.on('response', async (resp) => {
      if (!resp.url().includes('/api/route') || resp.request().method() !== 'POST') return
      try {
        const body = await resp.json()
        routingPolicies.push(body?.routingPolicy || null)
      } catch {
        routingPolicies.push(null)
      }
    })

    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.getByTestId('panel-settings')).toBeVisible({ timeout: 20000 })

    await updateAccessibilityProfile(page, {
      mobilityMode: 'WHEELCHAIR',
      avoidStairs: true,
      avoidSlope: true,
      avoidConstruction: true,
      maxSlopePercent: 8,
    })

    await setSearchPoint(page, '\u6559\u5b66\u697c', 'start')
    await setSearchPoint(page, '\u56fe\u4e66\u9986\u5165\u53e3', 'end')

    await expect.poll(
      () => routingPolicies.filter((p) =>
        p
        && p.profileApplied === true
        && p.profileMobilityMode === 'WHEELCHAIR'
        && p.strategy === 'BALANCED'
        && p.strategySource === 'REQUEST'
        && p.slopeWeightSource === 'PROFILE'
      ).length,
      { timeout: 30000 }
    ).toBeGreaterThan(0)
  })
})


