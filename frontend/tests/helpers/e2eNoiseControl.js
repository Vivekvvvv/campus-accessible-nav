const noisePatterns = [
  /\/api\/client\/error(?:\?.*)?$/i,
  /\/api\/navigation\/session\/[^/]+\/client-event(?:\?.*)?$/i,
  /^https:\/\/fonts\.googleapis\.com\//i,
  /^https:\/\/fonts\.gstatic\.com\//i,
]

function isNoiseUrl(url) {
  return noisePatterns.some((pattern) => pattern.test(url))
}

export async function installE2ENoiseInterceptors(page) {
  await page.route('**/api/client/error', async (route) => {
    await route.fulfill({
      status: 204,
      contentType: 'application/json',
      body: '',
    })
  })

  await page.route('https://fonts.googleapis.com/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/css; charset=utf-8',
      body: '/* mocked in e2e */',
    })
  })

  await page.route('https://fonts.gstatic.com/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'font/woff2',
      body: '',
    })
  })
}

export function attachPageDebug(page) {
  page.on('console', (msg) => {
    console.log(`[browser:${msg.type()}] ${msg.text()}`)
  })
  page.on('pageerror', (err) => {
    console.log(`[browser:pageerror] ${err?.stack || err}`)
  })
  page.on('requestfailed', (req) => {
    const url = req.url()
    const errorText = req.failure()?.errorText || 'unknown'
    if (isNoiseUrl(url)) return
    if (/\/api\/navigation\/session\/[^/]+\/reroute(?:\?.*)?$/i.test(url) && errorText === 'net::ERR_ABORTED') {
      return
    }
    console.log(`[browser:requestfailed] ${req.method()} ${url} -> ${errorText}`)
  })
}
