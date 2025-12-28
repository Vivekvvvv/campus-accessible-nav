import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import i18n from '../locales'
import { useToastStore } from './useToastStore'
import { useRouteStore } from './useRouteStore'
import { useSessionStore } from './useSessionStore'
import { apiJson } from '../services/apiService'
import { useFavoritesStorage, type FavoriteItem, type FavoriteState } from '../composables/useFavoritesStorage'
import { logger } from '../utils/logger'
import type { RoutePoint } from '../types/api'

function tr(key: string, params?: Record<string, unknown>): string {
  return i18n.global.t(key, params as Record<string, string>)
}

interface RemoteItem {
  id?: number
  name?: string
  lng?: number
  lat?: number
  groupName?: string
  groupId?: number | null
  tags?: unknown[]
  createdAt?: string | null
  updatedAt?: string | null
}

function normalizeRemoteItem(item: unknown): FavoriteItem | null {
  if (!item || typeof item !== 'object') return null
  const r = item as RemoteItem
  const lat = Number(r.lat)
  const lng = Number(r.lng)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null

  return {
    id: Number(r.id),
    name: String(r.name || '').trim(),
    lng,
    lat,
    group: String(r.groupName || '').trim(),
    groupId: r.groupId == null ? null : Number(r.groupId),
    tags: Array.isArray(r.tags) ? r.tags.map((x) => String(x || '').trim()).filter(Boolean) : [],
    createdAt: r.createdAt || null,
    updatedAt: r.updatedAt || null,
    _remote: true,
  }
}

function buildRemotePayload({ name, tags, point }: { name: string; tags: string[]; point: RoutePoint }) {
  return {
    name,
    lat: Number(point.lat),
    lng: Number(point.lng),
    tags,
    groupId: null,
  }
}

export const useFavoritesStore = defineStore('favorites', () => {
  const toast = useToastStore()
  const routeStore = useRouteStore()
  const sessionStore = useSessionStore()

  const favoriteState = ref<FavoriteState>({
    name: '',
    group: '',
    tagsText: '',
    filterText: '',
    importText: '',
    items: [],
    lastError: null,
    lastMessage: null,
    syncSource: 'local',
  })

  const filteredFavorites = computed(() => {
    const q = String(favoriteState.value.filterText || '').trim()
    if (!q) return favoriteState.value.items
    return favoriteState.value.items.filter((x) => String(x.group || '').includes(q) || String(x.name || '').includes(q))
  })

  async function loadPinyinFn(): Promise<((text: string, opts?: Record<string, unknown>) => string) | null> {
    try {
      const mod = await import('pinyin-pro')
      return (mod.pinyin || mod.default || null) as ((text: string, opts?: Record<string, unknown>) => string) | null
    } catch {
      return null
    }
  }

  const FAVORITES_KEY = 'accessiblenav_favorites'
  const { loadFavorites: loadLocalFavorites, persistFavorites } = useFavoritesStorage({
    favoriteState,
    favoritesKey: FAVORITES_KEY,
    getPinyinFn: loadPinyinFn as unknown as () => ((text: string, opts?: Record<string, unknown>) => string) | null,
  })

  async function loadRemoteFavorites(): Promise<FavoriteItem[]> {
    const response = await apiJson<Record<string, unknown>>('/api/favorites/places', { method: 'GET' })
    const payload = response && typeof response === 'object' && 'data' in response ? response.data : response
    const list = Array.isArray(payload) ? payload : []
    const normalized = list.map(normalizeRemoteItem).filter(Boolean) as FavoriteItem[]
    favoriteState.value.items = normalized
    persistFavorites(normalized)
    favoriteState.value.syncSource = 'remote'
    return normalized
  }

  async function loadFavorites(): Promise<void> {
    favoriteState.value.lastError = null
    favoriteState.value.lastMessage = null

    if (!sessionStore.isAuthenticated) {
      loadLocalFavorites()
      favoriteState.value.syncSource = 'local'
      return
    }

    try {
      await loadRemoteFavorites()
    } catch (e: unknown) {
      logger.warn('[Favorites] remote load failed, fallback to local', e)
      loadLocalFavorites()
      favoriteState.value.syncSource = 'local'
      favoriteState.value.lastError = (e as Error)?.message || String(e)
    }
  }

  async function addFavorite(which: 'start' | 'end'): Promise<void> {
    favoriteState.value.lastError = null

    const point = routeStore.points[which] as RoutePoint | null
    if (!point) {
      toast.push(tr('toast.setLocationFirst'))
      return
    }

    const name = (favoriteState.value.name as string || '').trim()
    const tags = ((favoriteState.value.tagsText as string) || '')
      .split(/[,;\s]+/)
      .map((x) => x.trim())
      .filter(Boolean)

    const prefix = which === 'start' ? tr('map.startPoint') : tr('map.endPoint')
    const label = name || `${prefix} (${Number(point.lng).toFixed(4)}, ${Number(point.lat).toFixed(4)})`

    let remoteSaved = false

    if (sessionStore.isAuthenticated) {
      try {
        const response = await apiJson<Record<string, unknown>>('/api/favorites/places', {
          method: 'POST',
          body: JSON.stringify(buildRemotePayload({
            name: label,
            tags,
            point,
          })),
        })
        const payload = response && typeof response === 'object' && 'data' in response ? response.data : response
        const created = normalizeRemoteItem(payload)
        if (created) {
          favoriteState.value.items.unshift(created)
          persistFavorites(favoriteState.value.items)
          favoriteState.value.syncSource = 'remote'
          remoteSaved = true
        }
      } catch (e: unknown) {
        logger.warn('[Favorites] remote add failed, fallback to local append', e)
        favoriteState.value.lastError = (e as Error)?.message || String(e)
      }
    }

    if (!sessionStore.isAuthenticated || !remoteSaved) {
      const localItem: FavoriteItem = {
        id: Date.now(),
        name: label,
        lng: Number(point.lng),
        lat: Number(point.lat),
        group: (favoriteState.value.group as string || '').trim(),
        tags,
        createdAt: new Date().toISOString(),
      }
      favoriteState.value.items.push(localItem)
      persistFavorites(favoriteState.value.items)
      favoriteState.value.syncSource = 'local'
    }

    toast.push(tr('toast.favoriteAdded', { label }))

    favoriteState.value.name = ''
    favoriteState.value.tagsText = ''
  }

  async function removeFavorite(id: number | string): Promise<void> {
    const idx = favoriteState.value.items.findIndex((x) => Number(x.id) === Number(id))
    if (idx === -1) return

    const current = favoriteState.value.items[idx]

    if (sessionStore.isAuthenticated && current?._remote) {
      try {
        await apiJson(`/api/favorites/places/${current.id}`, {
          method: 'DELETE',
        })
      } catch (e: unknown) {
        logger.warn('[Favorites] remote remove failed, fallback to local remove', e)
        favoriteState.value.lastError = (e as Error)?.message || String(e)
      }
    }

    favoriteState.value.items.splice(idx, 1)
    persistFavorites(favoriteState.value.items)
    toast.push(tr('toast.favoriteRemoved'))
  }

  function applyFavorite(item: FavoriteItem, isEnd: boolean): void {
    if (!item) return
    routeStore.setPoint(isEnd ? 'end' : 'start', {
      lng: item.lng,
      lat: item.lat,
      name: item.name,
    })
    toast.push(tr(isEnd ? 'toast.setAsEnd' : 'toast.setAsStart'))
  }

  function doImport(): void {
    const txt = favoriteState.value.importText
    if (!txt) return

    try {
      const list = JSON.parse(txt)
      if (Array.isArray(list)) {
        favoriteState.value.items = [...favoriteState.value.items, ...list]
        persistFavorites(favoriteState.value.items)
        favoriteState.value.importText = ''
        toast.push(tr('toast.importedFavorites', { count: list.length }))
      }
    } catch (e: unknown) {
      toast.push(tr('toast.importFormatError', { message: (e as Error)?.message || String(e) }))
    }
  }

  function doExport(): string {
    const str = JSON.stringify(favoriteState.value.items, null, 2)
    favoriteState.value.importText = str
    toast.push(tr('toast.filledBelow'))
    return str
  }

  return {
    favoriteState,
    filteredFavorites,
    loadFavorites,
    addFavorite,
    removeFavorite,
    applyFavorite,
    doImport,
    doExport,
  }
})
