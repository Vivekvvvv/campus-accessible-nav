import { type Ref } from 'vue'
import { applyCategories, attachPinyinToItem, type SearchableItem } from '../utils/searchUtils'

export interface FavoriteItem {
  id: number | string
  name: string
  lng: number
  lat: number
  group: string
  tags: string[]
  createdAt: string | null
  pinyin?: string
  initials?: string
  categories?: string[]
  [key: string]: unknown
}

export interface FavoriteState {
  items: FavoriteItem[]
  lastError: string | null
  lastMessage: string | null
  importText?: string
  [key: string]: unknown
}

export interface FavoritesStorageOptions {
  favoriteState: Ref<FavoriteState>
  favoritesKey: string
  getPinyinFn?: (() => ((text: string, opts?: Record<string, unknown>) => string) | null) | null
}

function favoriteKey(item: FavoriteItem): string {
  const name = String(item?.name || '').trim()
  const lng = Number(item?.lng)
  const lat = Number(item?.lat)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return name
  return `${lng.toFixed(6)},${lat.toFixed(6)}|${name}`
}

function normalizeFavoriteItems(list: unknown[], pinyinFn: ((text: string, opts?: Record<string, unknown>) => string) | null): FavoriteItem[] {
  const out: FavoriteItem[] = []
  let seq = Date.now()

  for (const x of list || []) {
    if (!x) continue
    const item = x as Record<string, unknown>
    const lng = Number(item.lng)
    const lat = Number(item.lat)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) continue

    const tags = Array.isArray(item.tags)
      ? (item.tags as unknown[]).map((t) => String(t).trim()).filter(Boolean)
      : typeof item.tags === 'string'
        ? (item.tags as string)
            .split(/[,\uFF0C\s]+/)
            .map((t) => t.trim())
            .filter(Boolean)
        : []

    out.push({
      id: (item.id as number | string) ?? seq++,
      name: item.name ? String(item.name) : '',
      lng,
      lat,
      group: item.group ? String(item.group) : '',
      tags,
      createdAt: (item.createdAt as string) || null,
    })
  }

  return out.map((item) => attachPinyinToItem(applyCategories(item as SearchableItem), pinyinFn) as FavoriteItem)
}

export function useFavoritesStorage({ favoriteState, favoritesKey, getPinyinFn }: FavoritesStorageOptions) {
  function loadFavorites(): void {
    favoriteState.value.lastError = null
    favoriteState.value.lastMessage = null

    try {
      const raw = localStorage.getItem(favoritesKey)
      if (!raw) {
        favoriteState.value.items = []
        return
      }

      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        const pinyinFn = getPinyinFn?.() || null
        favoriteState.value.items = normalizeFavoriteItems(parsed, pinyinFn)
        return
      }

      favoriteState.value.items = []
    } catch (e: unknown) {
      favoriteState.value.items = []
      favoriteState.value.lastError = (e as Error)?.message || String(e)
    }
  }

  function persistFavorites(items: FavoriteItem[]): void {
    localStorage.setItem(favoritesKey, JSON.stringify(items || []))
  }

  async function exportFavorites(): Promise<void> {
    favoriteState.value.lastError = null
    favoriteState.value.lastMessage = null

    const payload = JSON.stringify(favoriteState.value.items || [], null, 2)
    if (!payload || payload === '[]') {
      favoriteState.value.lastError = '暂无可导出的收藏'
      return
    }

    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        await navigator.clipboard.writeText(payload)
        favoriteState.value.lastMessage = '已复制 JSON 到剪贴板'
        return
      } catch {
        favoriteState.value.importText = payload
        favoriteState.value.lastMessage = '已填入下方文本框，可手动复制'
        return
      }
    }

    favoriteState.value.importText = payload
    favoriteState.value.lastMessage = '已填入下方文本框，可手动复制'
  }

  function importFavorites(): void {
    favoriteState.value.lastError = null
    favoriteState.value.lastMessage = null

    const raw = String(favoriteState.value.importText || '').trim()
    if (!raw) {
      favoriteState.value.lastError = '请先粘贴 JSON 内容'
      return
    }

    let parsed: unknown
    try {
      parsed = JSON.parse(raw)
    } catch {
      favoriteState.value.lastError = 'JSON 格式错误'
      return
    }

    if (!Array.isArray(parsed)) {
      favoriteState.value.lastError = 'JSON 需要为数组'
      return
    }

    const pinyinFn = getPinyinFn?.() || null
    const incoming = normalizeFavoriteItems(parsed, pinyinFn)

    const existing = favoriteState.value.items || []
    const seen = new Set(existing.map((x) => favoriteKey(x)))
    let added = 0

    const merged = [...existing]
    for (const item of incoming) {
      const key = favoriteKey(item)
      if (seen.has(key)) continue
      seen.add(key)
      merged.push(item)
      added++
    }

    favoriteState.value.items = merged
    persistFavorites(merged)
    favoriteState.value.lastMessage = `已导入 ${incoming.length} 条，新增 ${added} 条`
  }

  return {
    loadFavorites,
    persistFavorites,
    exportFavorites,
    importFavorites,
  }
}
