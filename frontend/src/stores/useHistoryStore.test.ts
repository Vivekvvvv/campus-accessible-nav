import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/utils/logger', () => ({
  logger: {
    debug: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    log: vi.fn(),
  },
}))

vi.mock('@/locales', () => ({
  default: {
    global: {
      locale: { value: 'zh-CN' },
      t: (key: string, params?: Record<string, unknown>) => {
        if (!params) return key
        const extra = Object.entries(params)
          .map(([k, v]) => `${k}=${String(v)}`)
          .join(',')
        return `${key}:${extra}`
      },
    },
  },
}))

describe('useHistoryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('loads history from localStorage and filters by keyword', async () => {
    localStorage.setItem(
      'accessiblenav_nav_history',
      JSON.stringify([
        {
          id: '1',
          start: { lng: 113.2, lat: 23.27, name: '图书馆' },
          end: { lng: 113.21, lat: 23.28, name: '食堂' },
          mode: 'walk',
          distanceM: 100,
          durationSec: 60,
          timestamp: Date.now(),
        },
      ])
    )

    const { useHistoryStore } = await import('@/stores/useHistoryStore')
    const store = useHistoryStore()

    expect(store.historyList).toHaveLength(1)
    store.setFilterKeyword('图书')
    expect(store.filteredHistory).toHaveLength(1)
    store.setFilterKeyword('宿舍')
    expect(store.filteredHistory).toHaveLength(0)
  })

  it('adds deduplicated history items and removes/clears them', async () => {
    const { useHistoryStore } = await import('@/stores/useHistoryStore')
    const store = useHistoryStore()

    store.addHistory({
      start: { lng: 113.2, lat: 23.27, name: 'A' },
      end: { lng: 113.21, lat: 23.28, name: 'B' },
      mode: 'walk',
      distanceM: 100,
      durationSec: 60,
    })
    store.addHistory({
      start: { lng: 113.2, lat: 23.27, name: 'A2' },
      end: { lng: 113.21, lat: 23.28, name: 'B2' },
      mode: 'wheel',
      distanceM: 120,
      durationSec: 80,
    })

    expect(store.historyList).toHaveLength(1)
    expect(store.historyList[0].mode).toBe('wheel')

    const id = store.historyList[0].id
    store.removeHistory(id)
    expect(store.historyList).toHaveLength(0)

    store.addHistory({
      start: { lng: 113.22, lat: 23.29, name: '' },
      end: { lng: 113.23, lat: 23.3, name: '' },
    })
    expect(store.historyList[0].start.name).toBe('map.unnamedPlace')

    store.clearHistory()
    expect(store.historyList).toEqual([])
    expect(localStorage.getItem('accessiblenav_nav_history')).toBe('[]')
  })

  it('formats relative and calendar time labels', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-18T12:00:00'))

    const { useHistoryStore } = await import('@/stores/useHistoryStore')
    const store = useHistoryStore()

    expect(store.formatTime(Date.now() - 30_000)).toBe('time.justNow')
    expect(store.formatTime(Date.now() - 5 * 60_000)).toBe('time.minutesAgo:count=5')
    expect(store.formatTime(Date.now() - 2 * 60 * 60_000)).toMatch(/\d{2}:\d{2}/)
    expect(store.formatTime(Date.now() - 24 * 60 * 60_000)).toContain('time.yesterdayAt')

    vi.useRealTimers()
  })
})
