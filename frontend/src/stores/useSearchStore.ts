import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import i18n from '../locales'
import { useRouteStore } from './useRouteStore'
import { useToastStore } from './useToastStore'
import { applyCategories, attachPinyinToItem, fuzzyMatch, normalizeSearchText, type SearchableItem } from '../utils/searchUtils'
import { logger } from '../utils/logger'

function tr(key: string, params?: Record<string, unknown>): string {
  return i18n.global.t(key, params as Record<string, string>)
}

interface SearchState {
  query: string
  which: 'start' | 'end'
  categories: string[]
}

interface CampusDataState {
  loading: boolean
  error: string | null
}

interface CampusItem extends SearchableItem {
  id: string | number
  lng: number
  lat: number
  aliases: string[]
  category?: string
  building?: string
  amenity?: string
}

export const useSearchStore = defineStore('search', () => {
  const routeStore = useRouteStore()
  const toast = useToastStore()

  const searchState = ref<SearchState>({
    query: '',
    which: 'start',
    categories: [],
  })

  // Data
  const campusItems = ref<CampusItem[]>([])
  const campusDataState = ref<CampusDataState>({
    loading: false,
    error: null,
  })

  // Make labels react to locale switches.
  const _locale = computed(() => i18n.global.locale.value)
  const categoryOptions = computed(() => {
    void _locale.value
    return [
      { key: 'teaching', label: tr('search.categoryTeaching') },
      { key: 'dorm', label: tr('search.categoryDorm') },
      { key: 'canteen', label: tr('search.categoryCanteen') },
      { key: 'service', label: tr('search.categoryService') },
    ]
  })

  const HISTORY_KEY = 'accessiblenav_search_history'
  const searchHistory = ref<string[]>([])

  // Pinyin loader
  let cachedPinyinFn: ((text: string, opts?: Record<string, unknown>) => string) | null = null
  async function loadPinyinFn(): Promise<typeof cachedPinyinFn> {
    if (cachedPinyinFn) return cachedPinyinFn
    try {
      const mod = await import('pinyin-pro')
      cachedPinyinFn = (mod.pinyin || mod.default || null) as typeof cachedPinyinFn
    } catch {
      cachedPinyinFn = null
    }
    return cachedPinyinFn
  }

  // Load History
  function loadHistory(): void {
    try {
      const raw = localStorage.getItem(HISTORY_KEY)
      if (raw) {
        searchHistory.value = JSON.parse(raw)
      }
    } catch {
      searchHistory.value = []
    }
  }

  function saveHistory(): void {
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(searchHistory.value.slice(0, 10)))
    } catch {
      // ignore
    }
  }

  function addToHistory(txt: string): void {
    const val = (txt || '').trim()
    if (!val) return
    const set = new Set([val, ...searchHistory.value])
    searchHistory.value = [...set].slice(0, 10)
    saveHistory()
  }

  function clearHistory(): void {
    searchHistory.value = []
    localStorage.removeItem(HISTORY_KEY)
  }

  // Load Data
  async function loadCampusData(): Promise<void> {
    if (campusItems.value.length > 2) return // already loaded (dummy check)

    campusDataState.value.loading = true
    campusDataState.value.error = null
    try {
      const urls = ['/data/gbuc-jianggao-buildings.geojson', '/data/gbuc-jianggao-poi.geojson']

      const pinyinFn = await loadPinyinFn()
      const allItems: CampusItem[] = []

      for (const url of urls) {
        try {
          const res = await fetch(url)
          if (!res.ok) continue
          const geo = await res.json()
          for (const feature of geo.features || []) {
            const props = feature.properties
            const geom = feature.geometry
            if (!props || !props.name) continue

            const center = pickGeometryPoint(geom)
            if (!center) continue

            const tags = buildTagsFromProps(props)

            allItems.push({
              id: props.osm_id || props.id || `gen-${Math.random()}`,
              name: props.name,
              lng: center.lng,
              lat: center.lat,
              aliases: [],
              tags,
              group: 'campus',
              category: props.category,
              building: props.building,
              amenity: props.amenity,
            })
          }
        } catch (e) {
          logger.error('Failed to load', url, e)
        }
      }

      const decorated = allItems.map((item) => attachPinyinToItem(applyCategories(item), pinyinFn) as CampusItem)
      campusItems.value = decorated
    } catch (err: unknown) {
      campusDataState.value.error = (err as Error)?.message || String(err)
    } finally {
      campusDataState.value.loading = false
    }
  }

  function averageCoords(coords: unknown[]): { lng: number; lat: number } | null {
    if (!Array.isArray(coords) || coords.length === 0) return null
    let sumLng = 0
    let sumLat = 0
    let count = 0
    for (const coord of coords) {
      const c = coord as [number, number]
      const lng = Number(c?.[0])
      const lat = Number(c?.[1])
      if (Number.isFinite(lng) && Number.isFinite(lat)) {
        sumLng += lng
        sumLat += lat
        count += 1
      }
    }
    return count ? { lng: sumLng / count, lat: sumLat / count } : null
  }

  function pickGeometryPoint(geometry: Record<string, unknown> | null): { lng: number; lat: number } | null {
    if (!geometry) return null
    if (geometry.type === 'Point') {
      const coordinates = geometry.coordinates as [number, number]
      const [lng, lat] = coordinates || []
      return Number.isFinite(lng) && Number.isFinite(lat) ? { lng, lat } : null
    }
    if (geometry.type === 'Polygon') {
      const coordinates = geometry.coordinates as number[][][]
      return averageCoords(coordinates?.[0] || [])
    }
    if (geometry.type === 'MultiPolygon') {
      const coordinates = geometry.coordinates as number[][][][]
      return averageCoords(coordinates?.[0]?.[0] || [])
    }
    return null
  }

  function buildTagsFromProps(props: Record<string, unknown>): string[] {
    const tags: string[] = []
    const keys = ['category', 'building', 'amenity', 'shop', 'office', 'tourism', 'leisure', 'public_transport', 'highway']
    for (const key of keys) {
      if (props?.[key]) tags.push(String(props[key]))
    }
    return tags
  }

  const searchResults = computed(() => {
    const q = normalizeSearchText(searchState.value.query)
    const cats = searchState.value.categories || []

    let list: CampusItem[] = campusItems.value
    if (cats.length > 0) {
      list = list.filter((item) => {
        if (!item.categories) return false
        return item.categories.some((c) => cats.includes(c))
      })
    }

    if (!q) return list
    return list.filter((item) => fuzzyMatch(item, q))
  })

  // Actions
  function setQuery(q: string): void {
    searchState.value.query = q
  }

  function setWhich(w: 'start' | 'end'): void {
    searchState.value.which = w
  }

  function toggleCategory(key: string): void {
    const idx = searchState.value.categories.indexOf(key)
    if (idx < 0) {
      searchState.value.categories.push(key)
    } else {
      searchState.value.categories.splice(idx, 1)
    }
  }

  function applySearchResult(payload: unknown): void {
    if (!payload) return
    let item = payload as CampusItem
    let target: 'start' | 'end' = searchState.value.which

    const p = payload as { item?: CampusItem; which?: 'start' | 'end' }
    if (p.item && p.which) {
      item = p.item
      target = p.which
    }

    if (!item) return

    routeStore.setPoint(target, {
      name: item.name || '',
      lng: item.lng,
      lat: item.lat,
    })

    toast.push(target === 'start' ? tr('toast.startPointSet') : tr('toast.endPointSet'))
    addToHistory(item.name || searchState.value.query)
  }

  return {
    searchState,
    searchResults,
    searchHistory,
    campusItems,
    campusDataState,
    categoryOptions,
    loadCampusData,
    loadHistory,
    setQuery,
    setWhich,
    toggleCategory,
    applySearchResult,
    clearHistory,
    addToHistory,
  }
})
