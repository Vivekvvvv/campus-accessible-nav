import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const toastPush = vi.fn()
const persistFavorites = vi.fn()
const loadFavoritesMock = vi.fn()

vi.mock('@/services/apiService', () => ({
  apiJson: vi.fn(),
}))

vi.mock('@/locales', () => ({
  default: {
    global: {
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

vi.mock('@/stores/useToastStore', () => ({
  useToastStore: () => ({
    push: toastPush,
  }),
}))

vi.mock('@/composables/useFavoritesStorage', () => ({
  useFavoritesStorage: ({ favoriteState }: { favoriteState: { value: { items: unknown[] } } }) => ({
    loadFavorites: () => {
      loadFavoritesMock()
      favoriteState.value.items = [
        {
          id: 1,
          name: 'Local Favorite',
          lng: 113.2,
          lat: 23.27,
          group: '',
          tags: [],
          createdAt: '2026-01-01T00:00:00Z',
        },
      ]
    },
    persistFavorites,
  }),
}))

import { apiJson } from '@/services/apiService'
import { useFavoritesStore } from '@/stores/useFavoritesStore'
import { useRouteStore } from '@/stores/useRouteStore'
import { useSessionStore } from '@/stores/useSessionStore'

describe('useFavoritesStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  function authenticate() {
    const sessionStore = useSessionStore()
    sessionStore.userProfile = { username: 'user-a', role: 'USER' }
  }

  it('loads local favorites when user is unauthenticated', async () => {
    const store = useFavoritesStore()

    await store.loadFavorites()

    expect(loadFavoritesMock).toHaveBeenCalled()
    expect(store.favoriteState.syncSource).toBe('local')
    expect(store.favoriteState.items).toHaveLength(1)
  })

  it('loads remote favorites when user is authenticated', async () => {
    authenticate()
    const store = useFavoritesStore()
    vi.mocked(apiJson).mockResolvedValueOnce({
      data: [
        {
          id: 9,
          name: 'Remote Favorite',
          lng: 113.25,
          lat: 23.29,
          groupName: 'remote',
          tags: ['tag-a'],
        },
      ],
    })

    await store.loadFavorites()

    expect(apiJson).toHaveBeenCalledWith('/api/favorites/places', { method: 'GET' })
    expect(store.favoriteState.syncSource).toBe('remote')
    expect(store.favoriteState.items[0]).toMatchObject({
      id: 9,
      name: 'Remote Favorite',
      lng: 113.25,
      lat: 23.29,
      _remote: true,
    })
    expect(persistFavorites).toHaveBeenCalled()
  })

  it('adds local favorite when no session is available', async () => {
    const routeStore = useRouteStore()
    routeStore.setPoint('start', { lng: 113.2, lat: 23.27, name: 'Start' })
    const store = useFavoritesStore()
    store.favoriteState.name = 'My Start'
    store.favoriteState.tagsText = 'a,b'

    await store.addFavorite('start')

    expect(store.favoriteState.items).toHaveLength(1)
    expect(store.favoriteState.items[0]).toMatchObject({
      name: 'My Start',
      tags: ['a', 'b'],
    })
    expect(store.favoriteState.syncSource).toBe('local')
    expect(toastPush).toHaveBeenCalledWith('toast.favoriteAdded:label=My Start')
  })

  it('adds and removes remote favorites when authenticated', async () => {
    authenticate()
    const routeStore = useRouteStore()
    routeStore.setPoint('end', { lng: 113.201, lat: 23.271, name: 'End' })
    const store = useFavoritesStore()

    vi.mocked(apiJson).mockResolvedValueOnce({
      data: {
        id: 11,
        name: 'Remote End',
        lng: 113.201,
        lat: 23.271,
        groupName: '',
        tags: [],
      },
    })

    await store.addFavorite('end')

    expect(store.favoriteState.items[0]).toMatchObject({
      id: 11,
      name: 'Remote End',
      _remote: true,
    })

    vi.mocked(apiJson).mockResolvedValueOnce(undefined)
    await store.removeFavorite(11)

    expect(apiJson).toHaveBeenLastCalledWith('/api/favorites/places/11', { method: 'DELETE' })
    expect(store.favoriteState.items).toHaveLength(0)
    expect(toastPush).toHaveBeenLastCalledWith('toast.favoriteRemoved')
  })
})
