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

test.describe('Frontend UI Regression', () => {
  test('search + route compare + report export + share', async ({ page }) => {
    await page.route('**/api/route', async (route) => {
      console.log('>>> [Spy] POST /api/route')
      console.log('>>> [Spy] Request:', route.request().postData())
      const response = await route.fetch()
      console.log('>>> [Spy] Status:', response.status())
      const body = await response.text()
      console.log('>>> [Spy] Response:', body)
      await route.fulfill({ response, body })
    })

    attachPageDebug(page)
    await page.goto('/', { waitUntil: 'domcontentloaded' })

    await expect(page.getByTestId('panel-search')).toBeVisible({ timeout: 20000 })

    await setSearchPoint(page, '\u6559\u5b66\u697c1', 'start')
    await setSearchPoint(page, '\u56fe\u4e66\u9986\u5165\u53e3', 'end')

    await expect(page.getByTestId('route-mode-walk')).toBeVisible({ timeout: 20000 })
    await expect(page.getByTestId('route-mode-wheel')).toBeVisible()

    const reportPanel = page.getByTestId('panel-report')
    await openDetails(reportPanel)

    const reportToggle = page.getByTestId('report-toggle')
    await reportToggle.scrollIntoViewIfNeeded()
    await expect(reportToggle).toBeEnabled()
    await reportToggle.click()
    await expect(page.getByTestId('route-report-card')).toBeVisible()

    const downloadPromise = page.waitForEvent('download')
    await page.getByTestId('report-export-image').click()
    const download = await downloadPromise
    await expect(download.suggestedFilename()).toMatch(/route-report-.*\.png/)

    const settingsSection = page.getByTestId('panel-settings')
    await openDetails(settingsSection)

    await settingsSection.getByTestId('tool-copy-link').click()
    await expect.poll(
      async () => {
        try {
          return await page.evaluate(() => navigator.clipboard.readText())
        } catch {
          return ''
        }
      },
      { timeout: 10000 }
    ).toContain(page.url())

    await settingsSection.getByTestId('tool-open-qr').click()
    await expect(page.locator('img[alt="share qr"]')).toBeVisible()
  })

  test('favorites grouping and import/export', async ({ page }) => {
    attachPageDebug(page)
    await page.goto('/', { waitUntil: 'domcontentloaded' })

    await expect(page.getByTestId('panel-search')).toBeVisible({ timeout: 20000 })

    await setSearchPoint(page, '\u6559\u5b66\u697c1', 'start')

    const favoriteSection = page.getByTestId('panel-favorites')
    await openDetails(favoriteSection)

    const settingsSection = page.getByTestId('panel-settings')
    await openDetails(settingsSection)

    await favoriteSection.getByTestId('favorite-name').fill('\u6d4b\u8bd5\u6536\u85cf')
    await favoriteSection.getByTestId('favorite-group').fill('\u6559\u5b66\u697c')
    await favoriteSection.getByTestId('favorite-tags').fill('\u65e0\u969c\u788d')
    await favoriteSection.getByTestId('favorite-add-start').click()

    const favCard = favoriteSection.getByTestId('favorite-card').filter({ hasText: '\u6d4b\u8bd5\u6536\u85cf' }).first()
    await expect(favCard).toBeVisible()

    await favoriteSection.getByTestId('favorite-filter').fill('\u6559\u5b66\u697c')
    await expect(favCard).toBeVisible()

    await favoriteSection.getByTestId('favorite-export').click()
    await expect(favoriteSection.getByTestId('favorite-import-text')).toHaveValue(/\u6d4b\u8bd5\u6536\u85cf/)

    const importPayload = JSON.stringify([
      {
        name: '\u5bfc\u5165\u70b9',
        lng: 113.2048,
        lat: 23.2754,
        group: '\u6559\u5b66\u697c',
        tags: ['\u65e0\u969c\u788d'],
      },
    ])
    await favoriteSection.getByTestId('favorite-import-text').fill(importPayload)
    await favoriteSection.getByTestId('favorite-import').click()
    await expect(favoriteSection.getByText('\u5bfc\u5165\u70b9')).toBeVisible()

    await favCard.getByTestId('favorite-delete').click()
  })
})
