import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const toastPush = vi.fn()

vi.mock('@/locales', () => ({
  default: {
    global: {
      locale: { value: 'zh-CN' },
      t: (key: string) => key,
    },
  },
}))

vi.mock('@/stores/useToastStore', () => ({
  useToastStore: () => ({
    push: toastPush,
  }),
}))

describe('useSearchStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('loads, adds and clears search history', async () => {
    localStorage.setItem('accessiblenav_search_history', JSON.stringify(['A', 'B']))
    const { useSearchStore } = await import('@/stores/useSearchStore')
    const store = useSearchStore()

    store.loadHistory()
    expect(store.searchHistory).toEqual(['A', 'B'])

    store.addToHistory('C')
    expect(store.searchHistory[0]).toBe('C')

    store.clearHistory()
    expect(store.searchHistory).toEqual([])
    expect(localStorage.getItem('accessiblenav_search_history')).toBeNull()
  })

  it('toggles categories and applies search result to route points', async () => {
    const { useSearchStore } = await import('@/stores/useSearchStore')
    const { useRouteStore } = await import('@/stores/useRouteStore')
    const store = useSearchStore()
    const routeStore = useRouteStore()

    store.toggleCategory('service')
    expect(store.searchState.categories).toContain('service')
    store.toggleCategory('service')
    expect(store.searchState.categories).not.toContain('service')

    store.applySearchResult({
      name: 'Library',
      lng: 113.2,
      lat: 23.27,
    })

    expect(routeStore.points.start).toMatchObject({
      name: 'Library',
      lng: 113.2,
      lat: 23.27,
    })
    expect(toastPush).toHaveBeenCalledWith('toast.startPointSet')
    expect(store.searchHistory[0]).toBe('Library')
  })

  it('loads campus geojson data and computes search results', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          features: [
            {
              properties: {
                id: 1,
                name: '图书馆',
                category: 'teaching',
              },
              geometry: {
                type: 'Point',
                coordinates: [113.2, 23.27],
              },
            },
          ],
        }),
        { status: 200 }
      )
    )

    const { useSearchStore } = await import('@/stores/useSearchStore')
    const store = useSearchStore()

    await store.loadCampusData()
    expect(store.campusItems).toHaveLength(1)
    expect(store.campusItems[0]?.name).toBe('图书馆')

    store.setQuery('图书馆')
    expect(store.searchResults.some((item) => item.name === '图书馆')).toBe(true)
  })
})
