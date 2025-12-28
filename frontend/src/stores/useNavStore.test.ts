import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick, reactive } from 'vue'

const toastPush = vi.fn()
const navSessionStoreMock = reactive({
  lastEvent: null as { type: string; message: string; ts: number } | null,
  isActive: true,
  reportClientEvent: vi.fn(),
  handleLocationUpdate: vi.fn(),
})

vi.mock('@/services/apiService', () => ({
  apiJson: vi.fn(),
}))

vi.mock('@/services/voiceService', () => ({
  voiceService: {
    updateSettings: vi.fn(),
    speakImmediate: vi.fn(),
    speak: vi.fn(),
    speakArrival: vi.fn(),
    speakDeviation: vi.fn(),
    speakNavigationInstruction: vi.fn(),
  },
}))

vi.mock('@/stores/useToastStore', () => ({
  useToastStore: () => ({
    push: toastPush,
  }),
}))

vi.mock('@/stores/useNavigationSessionStore', () => ({
  useNavigationSessionStore: () => navSessionStoreMock,
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

import { apiJson } from '@/services/apiService'
import { voiceService } from '@/services/voiceService'
import { useNavStore } from '@/stores/useNavStore'
import { useSessionStore } from '@/stores/useSessionStore'

describe('useNavStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    navSessionStoreMock.lastEvent = null
    navSessionStoreMock.isActive = true
    Object.defineProperty(navigator, 'vibrate', {
      configurable: true,
      value: vi.fn(),
    })
  })

  function authenticate() {
    const sessionStore = useSessionStore()
    sessionStore.userProfile = { username: 'user-a', role: 'USER' }
  }

  it('cycles tracking mode and pushes matching toast messages', () => {
    const store = useNavStore()

    store.toggleTracking()
    expect(store.trackingMode).toBe('follow')
    expect(toastPush).toHaveBeenLastCalledWith('toast.trackingFollow')

    store.toggleTracking()
    expect(store.trackingMode).toBe('heading')
    expect(toastPush).toHaveBeenLastCalledWith('toast.trackingHeading')

    store.toggleTracking()
    expect(store.trackingMode).toBe('none')
    expect(toastPush).toHaveBeenLastCalledWith('toast.trackingStop')
  })

  it('toggles voice and persists policy when authenticated', async () => {
    authenticate()
    const store = useNavStore()
    vi.mocked(apiJson).mockResolvedValue({ enabled: true, vibrateEnabled: true })

    store.toggleVoice(true)
    await nextTick()
    await Promise.resolve()

    expect(store.voiceEnabled).toBe(true)
    expect(voiceService.updateSettings).toHaveBeenCalledWith({ enabled: true })
    expect(voiceService.speakImmediate).toHaveBeenCalledWith('toast.voiceOn')
    expect(apiJson).toHaveBeenCalledWith('/api/v1/voice-settings', expect.objectContaining({
      method: 'PUT',
    }))
  })

  it('loads voice policy for authenticated users', async () => {
    authenticate()
    const store = useNavStore()
    vi.mocked(apiJson).mockResolvedValue({
      data: {
        enabled: true,
        vibrateEnabled: false,
        preTurnM: 40,
        preArrivalM: 60,
        announceIntervalM: 30,
        quietHoursStart: '22:00',
        quietHoursEnd: '06:00',
      },
    })

    await store.loadVoicePolicy()

    expect(store.voiceEnabled).toBe(true)
    expect(store.vibrateEnabled).toBe(false)
    expect(store.voicePolicy.preTurnM).toBe(40)
    expect(store.voicePolicy.preArrivalM).toBe(60)
    expect(store.voicePolicy.announceIntervalM).toBe(30)
  })

  it('reacts to navigation session events with vibration and voice output', async () => {
    const store = useNavStore()
    store.toggleVoice(true)
    await nextTick()

    navSessionStoreMock.lastEvent = {
      type: 'REROUTED',
      message: 'rerouted',
      ts: Date.now(),
    }
    await nextTick()

    expect(voiceService.speakDeviation).toHaveBeenCalled()
    expect(navigator.vibrate).toHaveBeenCalled()
    expect(navSessionStoreMock.reportClientEvent).toHaveBeenCalledWith('VIBRATION_TRIGGERED', expect.any(String))
  })

  it('handles location permission denied by stopping locating and showing toast', () => {
    const store = useNavStore()
    const watchPosition = vi.fn((_ok, onError) => {
      onError({ code: 1 })
      return 7
    })
    const clearWatch = vi.fn()
    ;(navigator as Navigator & {
      geolocation: {
        getCurrentPosition: ReturnType<typeof vi.fn>
        watchPosition: typeof watchPosition
        clearWatch: typeof clearWatch
      }
    }).geolocation = {
      getCurrentPosition: vi.fn(),
      watchPosition,
      clearWatch,
    }

    store.startLocationWatch()

    expect(store.isLocating).toBe(false)
    expect(toastPush).toHaveBeenCalledWith('toast.locatingPermissionDenied')
    expect(watchPosition).toHaveBeenCalled()
  })
})
