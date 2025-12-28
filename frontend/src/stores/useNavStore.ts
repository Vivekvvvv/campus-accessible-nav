import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { useToastStore } from './useToastStore'
import { useRouteStore } from './useRouteStore'
import { useNavigationSessionStore } from './useNavigationSessionStore'
import { useSessionStore } from './useSessionStore'
import { projectToLineStringMeters } from '../utils/polylineUtils'
import { getDistance } from '../utils/coordTransform'
import { logger } from '../utils/logger'
import { apiJson } from '../services/apiService'
import { voiceService } from '../services/voiceService'
import { formatDistanceM } from './navigationSessionHelpers'
import {
  buildInstructionTimeline,
  clamp,
  DEFAULT_VOICE_POLICY,
  defaultNavInfo,
  extractLineCoordinates,
  formatDurationSec,
  isInQuietHours,
  isTurnAction,
  normalizeVoicePolicy,
  routeSignature,
  unwrapApiPayload,
} from './navStoreHelpers'
import i18n from '../locales'
import type { NavInfo, VoicePolicy } from './types/navigation'
import type { UserLocation, RouteResponse } from '../types/api'

const PROGRESS_ANNOUNCE_EVERY_M = 20
const MIN_SPEAK_INTERVAL_MS = 8000
const TURN_ANNOUNCE_WITHIN_M = 12
const PRE_ARRIVAL_ANNOUNCE_WITHIN_M = 20

function tr(key: string, params?: Record<string, unknown>): string {
  return i18n.global.t(key, params as Record<string, string>)
}

export const useNavStore = defineStore('nav', () => {
  const toast = useToastStore()
  const routeStore = useRouteStore()
  const navSessionStore = useNavigationSessionStore()
  const sessionStore = useSessionStore()

  // State
  const userLocation = ref<UserLocation | null>(null)
  const trackingMode = ref<'none' | 'follow' | 'heading'>('none')
  const isLocating = ref(false)

  // Guidance state for UI
  const navInfo = ref<NavInfo>(defaultNavInfo())

  // Settings
  const voiceEnabled = ref(false)
  const vibrateEnabled = ref(true)
  const voicePolicy = ref<VoicePolicy>({ ...DEFAULT_VOICE_POLICY })

  // Internals
  let watchId: number | null = null
  let lastEventTs = 0
  const lastSpokenAtMs = ref(0)
  const lastProgressAnnouncedAtM = ref(0)
  const lastTurnAnnouncedIndex = ref(-1)
  const lastRouteSig = ref('')

  const hasActiveSession = computed(() => Boolean(navSessionStore?.isActive))

  // --- Task 2.3: Enhanced vibration patterns ---
  function eventVibratePattern(type: string): number | number[] {
    switch (String(type || '')) {
      case 'SESSION_STARTED':
        return 150
      case 'SESSION_PAUSED':
        return 80
      case 'SESSION_RESUMED':
        return 120
      case 'HAZARD_AHEAD':
        return [120, 80, 120, 80, 120]
      case 'REROUTED':
        return [200, 120, 200]
      case 'SESSION_ENDED':
        return [300, 100, 300]             // 双长振 = 到达
      case 'OFF_ROUTE':
        return [150, 100, 150, 100, 150]   // 新增
      case 'TURN_APPROACHING':
        return [80, 60, 80]                // 新增
      default:
        return 120
    }
  }

  // --- Task 2.1 + 2.2: voiceService integration + semantic announcements ---
  // Sync voiceEnabled to voiceService
  watch(voiceEnabled, (enabled) => {
    voiceService.updateSettings({ enabled })
  })

  // Bridge navigation-session events to voice/vibration strategy.
  watch(
    () => navSessionStore.lastEvent,
    (ev) => {
      if (!ev || !ev.ts) return
      if (ev.ts <= lastEventTs) return
      lastEventTs = ev.ts

      // Always vibrate on events
      vibrate(eventVibratePattern(ev.type))

      if (!voiceEnabled.value || isInQuietHours(voicePolicy.value)) return

      // Task 2.2: Dispatch to voiceService semantic methods based on event type
      const type = String(ev.type || '')
      if (type === 'SESSION_ENDED' && ev.message?.includes(tr('navigation.arrived'))) {
        voiceService.speakArrival()
      } else if (type === 'REROUTED' || type === 'REROUTING') {
        voiceService.speakDeviation()
      } else if (type === 'HAZARD_AHEAD' && ev.message) {
        voiceService.speakNavigationInstruction(ev.message)
      } else if (ev.message) {
        voiceService.speak(ev.message, { priority: 'normal' })
      }
      lastSpokenAtMs.value = Date.now()
    },
    { deep: false }
  )

  function computeGuidance(route: RouteResponse | null, loc: UserLocation): NavInfo {
    const coords = extractLineCoordinates(route)
    const proj = coords ? projectToLineStringMeters(loc, coords) : null

    const distanceM = Number(route?.distanceM)
    const durationSec = Number(route?.durationSec)

    // Fallback: if no projection, just compute remaining to end point.
    if (!proj) {
      const end = routeStore.points.end
      const remaining = end
        ? getDistance(Number(loc?.lng), Number(loc?.lat), Number(end.lng), Number(end.lat))
        : null
      return {
        ...defaultNavInfo(),
        remainingDistanceM: Number.isFinite(Number(remaining)) ? Number(remaining) : null,
        remainingDistanceText: Number.isFinite(Number(remaining)) ? formatDistanceM(remaining!) : '--',
      }
    }

    const totalLineM = proj.totalM
    const alongM = Math.max(0, Math.min(totalLineM, proj.alongM))
    const frac = totalLineM > 0 ? alongM / totalLineM : 0

    const remainingDistance = Number.isFinite(distanceM)
      ? Math.max(0, distanceM * (1 - frac))
      : Math.max(0, totalLineM - alongM)
    const remainingDuration = Number.isFinite(durationSec) ? Math.max(0, durationSec * (1 - frac)) : null

    const timeline = buildInstructionTimeline(route?.instructions, totalLineM)
    const goStep = timeline.find((s) => s.action === 'GO' && alongM >= s.startM && alongM < s.endM) || null
    const nextTurnOrArrive =
      timeline.find((s) => (isTurnAction(s.action) || s.action === 'ARRIVE') && s.atM >= alongM) || null

    const remainingToNext = nextTurnOrArrive ? Math.max(0, nextTurnOrArrive.atM - alongM) : null
    const nearTurn = Number.isFinite(remainingToNext) && remainingToNext! <= TURN_ANNOUNCE_WITHIN_M

    const currentText =
      nearTurn && nextTurnOrArrive
        ? nextTurnOrArrive.action === 'ARRIVE'
          ? tr('navigation.arriveSoon')
          : tr('navigation.ahead', { text: nextTurnOrArrive.text || '' })
        : goStep?.text || tr('navigation.followRoute')

    const nextText = nearTurn ? goStep?.text || '--' : nextTurnOrArrive?.text || '--'

    return {
      currentText,
      nextText,
      remainingDistanceM: remainingDistance,
      remainingDurationSec: remainingDuration,
      remainingDistanceText: formatDistanceM(remainingDistance),
      remainingDurationText: remainingDuration == null ? '--' : formatDurationSec(remainingDuration),
      remainingToNextM: remainingToNext,
      remainingToNextText: remainingToNext == null ? '--' : formatDistanceM(remainingToNext!),
      _meta: { totalLineM, alongM, nearTurn, nextIdx: nextTurnOrArrive?.idx ?? null, remainingToNextM: remainingToNext ?? null },
    }
  }

  function maybeAnnounceGuidance(info: NavInfo): void {
    if (!info || !voiceEnabled.value || !isLocating.value) return
    if (!hasActiveSession.value) return
    if (isInQuietHours(voicePolicy.value)) return

    const now = Date.now()
    if (now - lastSpokenAtMs.value < 1500) return // hard spam guard

    const meta = info._meta
    const alongM = Number(meta?.alongM)
    const turnThresholdM = clamp(voicePolicy.value.preTurnM, 0, 500, TURN_ANNOUNCE_WITHIN_M)
    const nearTurn = Boolean(meta?.remainingToNextM != null ? Number(meta.remainingToNextM) <= turnThresholdM : meta?.nearTurn)
    const nextIdx = Number.isFinite(Number(meta?.nextIdx)) ? Number(meta!.nextIdx) : null
    const arrivalThresholdM = clamp(voicePolicy.value.preArrivalM, 0, 1000, PRE_ARRIVAL_ANNOUNCE_WITHIN_M)
    const nearArrival = Number.isFinite(Number(info.remainingDistanceM)) && Number(info.remainingDistanceM) <= arrivalThresholdM

    // Turn announcement: only once per turn index.
    if ((nearTurn || nearArrival) && nextIdx != null && nextIdx !== lastTurnAnnouncedIndex.value) {
      if (now - lastSpokenAtMs.value >= 2500) {
        // Task 2.2: Use voiceService for turn/arrival announcements
        if (nearArrival) {
          voiceService.speakArrival()
        } else {
          voiceService.speakNavigationInstruction(info.currentText)
        }
        vibrate([80, 60, 80])
        lastSpokenAtMs.value = now
        lastTurnAnnouncedIndex.value = nextIdx
        void navSessionStore.reportClientEvent?.('TURN_ANNOUNCED', info.currentText)
      }
      return
    }

    // Progress announcement (every 20m).
    const announceEveryM = clamp(voicePolicy.value.announceIntervalM, 0, 500, PROGRESS_ANNOUNCE_EVERY_M)
    if (Number.isFinite(alongM) && alongM - lastProgressAnnouncedAtM.value >= announceEveryM) {
      if (now - lastSpokenAtMs.value >= MIN_SPEAK_INTERVAL_MS) {
        const msg =
          info.currentText && info.currentText !== '--'
            ? info.currentText
            : tr('navigation.remainingDistance', { distance: info.remainingDistanceText })
        // Task 2.2: Use voiceService for progress announcements
        voiceService.speak(msg, { priority: 'normal' })
        lastSpokenAtMs.value = now
        lastProgressAnnouncedAtM.value = alongM
        void navSessionStore.reportClientEvent?.('PROGRESS_ANNOUNCED', info.remainingDistanceText)
      }
    }
  }

  // Keep guidance up to date with location + active route.
  watch(
    [() => userLocation.value, () => routeStore.activeRoute],
    ([loc, route]) => {
      if (!loc) {
        navInfo.value = defaultNavInfo()
        return
      }

      if (!route) {
        navInfo.value = computeGuidance(null, loc as UserLocation)
        return
      }

      // Reset announcement counters when route changes (e.g., reroute).
      const sig = routeSignature(route as RouteResponse | null)
      if (sig !== lastRouteSig.value) {
        lastRouteSig.value = sig
        lastProgressAnnouncedAtM.value = 0
        lastTurnAnnouncedIndex.value = -1
      }

      const info = computeGuidance(route as RouteResponse, loc as UserLocation)
      navInfo.value = info
      maybeAnnounceGuidance(info)
    },
    { deep: false }
  )

  // Actions
  function setUserLocation(loc: UserLocation): void {
    userLocation.value = loc
  }

  function toggleTracking(): void {
    if (trackingMode.value === 'none') {
      trackingMode.value = 'follow'
      toast.push(tr('toast.trackingFollow'))
    } else if (trackingMode.value === 'follow') {
      trackingMode.value = 'heading'
      toast.push(tr('toast.trackingHeading'))
    } else {
      trackingMode.value = 'none'
      toast.push(tr('toast.trackingStop'))
    }
  }

  function toggleVoice(next?: boolean): void {
    if (typeof next === 'boolean') {
      voiceEnabled.value = next
    } else {
      voiceEnabled.value = !voiceEnabled.value
    }
    if (voiceEnabled.value) {
      speak(tr('toast.voiceOn'), { force: true })
    } else {
      toast.push(tr('toast.voiceOff'))
    }

    if (sessionStore.isAuthenticated) {
      void saveVoicePolicy({ enabled: voiceEnabled.value }).catch(() => {})
    }
  }

  function toggleVibration(next?: boolean): void {
    if (typeof next === 'boolean') {
      vibrateEnabled.value = next
    } else {
      vibrateEnabled.value = !vibrateEnabled.value
    }
    if (vibrateEnabled.value) {
      vibrate(200)
      toast.push(tr('toast.vibrateOn'))
    } else {
      toast.push(tr('toast.vibrateOff'))
    }

    if (sessionStore.isAuthenticated) {
      void saveVoicePolicy({ vibrateEnabled: vibrateEnabled.value }).catch(() => {})
    }
  }

  // Device APIs
  function startLocationWatch(): void {
    if (!navigator.geolocation) {
      toast.push(tr('toast.locatingNotSupported'))
      return
    }
    stopLocationWatch()
    isLocating.value = true

    watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const { latitude, longitude, heading, accuracy } = pos.coords
        userLocation.value = {
          lng: longitude,
          lat: latitude,
          heading: heading || 0,
          accuracy,
        }

        // Push location to backend / perform off-route reroute if a navigation session is active.
        Promise.resolve(navSessionStore.handleLocationUpdate(userLocation.value)).catch(() => {
          // ignore
        })
      },
      (err) => {
        if (err.code === 1) { // PERMISSION_DENIED
          logger.warn('Location permission denied', err)
          toast.push(tr('toast.locatingPermissionDenied'))
          isLocating.value = false
        }
      },
      {
        enableHighAccuracy: true,
        maximumAge: 1000,
        timeout: 10000,
      }
    )
  }

  function stopLocationWatch(): void {
    if (watchId !== null) {
      navigator.geolocation.clearWatch(watchId)
      watchId = null
    }
    isLocating.value = false
  }

  // --- Task 2.1: speak() now delegates to voiceService ---
  function speak(text: string, opts: { force?: boolean; allowInQuietHours?: boolean } = {}): void {
    const t = String(text || '').trim()
    if (!t) return
    if (!voiceEnabled.value && !opts.force) return
    if (isInQuietHours(voicePolicy.value) && !opts.allowInQuietHours) return
    voiceService.speakImmediate(t)

    // Task 2.4: Report voice announcement metric
    navSessionStore.reportClientEvent?.('VOICE_ANNOUNCED', t.slice(0, 60))
  }

  function vibrate(pattern: number | number[] = 200): void {
    if (!vibrateEnabled.value) return
    if (!voicePolicy.value.vibrateEnabled) return
    if (navigator.vibrate) {
      navigator.vibrate(pattern)

      // Task 2.4: Report vibration metric
      navSessionStore.reportClientEvent?.('VIBRATION_TRIGGERED', String(pattern))
    }
  }

  async function loadVoicePolicy(): Promise<void> {
    if (!sessionStore.isAuthenticated) {
      voicePolicy.value = { ...DEFAULT_VOICE_POLICY }
      vibrateEnabled.value = DEFAULT_VOICE_POLICY.vibrateEnabled
      return
    }

    try {
      const resp = await apiJson('/api/v1/voice-settings', { method: 'GET' })
      const payload = unwrapApiPayload(resp)
      voicePolicy.value = normalizeVoicePolicy(payload)
      if (typeof payload?.enabled === 'boolean') {
        voiceEnabled.value = payload.enabled as boolean
      }
      if (typeof payload?.vibrateEnabled === 'boolean') {
        vibrateEnabled.value = payload.vibrateEnabled as boolean
      }
    } catch (e) {
      logger.warn('[NavStore] load voice policy failed', e)
    }
  }

  async function saveVoicePolicy(patch: Record<string, unknown>): Promise<void> {
    if (!sessionStore.isAuthenticated || !patch || typeof patch !== 'object') return

    try {
      const resp = await apiJson('/api/v1/voice-settings', {
        method: 'PUT',
        body: JSON.stringify(patch),
      })
      const payload = unwrapApiPayload(resp)
      voicePolicy.value = normalizeVoicePolicy(payload)
      if (typeof payload?.enabled === 'boolean') {
        voiceEnabled.value = payload.enabled as boolean
      }
      if (typeof payload?.vibrateEnabled === 'boolean') {
        vibrateEnabled.value = payload.vibrateEnabled as boolean
      }
    } catch (e) {
      logger.warn('[NavStore] save voice policy failed', e)
      throw e
    }
  }

  watch(
    () => sessionStore.isAuthenticated,
    (authed) => {
      if (authed) {
        void loadVoicePolicy()
      } else {
        voicePolicy.value = { ...DEFAULT_VOICE_POLICY }
        voiceEnabled.value = false
        vibrateEnabled.value = DEFAULT_VOICE_POLICY.vibrateEnabled
      }
    },
    { immediate: false }
  )

  // Helper for components to request "Mock" location for testing
  function setMockLocation(lng: number, lat: number): void {
    userLocation.value = {
      lng,
      lat,
      heading: 0,
      accuracy: 10,
    }
  }

  return {
    userLocation,
    trackingMode,
    isLocating,
    voiceEnabled,
    vibrateEnabled,
    voicePolicy,
    navInfo,
    setUserLocation,
    toggleTracking,
    toggleVoice,
    toggleVibration,
    startLocationWatch,
    stopLocationWatch,
    speak,
    vibrate,
    loadVoicePolicy,
    saveVoicePolicy,
    setMockLocation,
  }
})
