<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouteStore } from '../stores/useRouteStore'
import { useNavigationSessionStore } from '../stores/useNavigationSessionStore'
import {
  buildLevelHintText,
  formatCoordsText,
  formatHazardText,
  formatOffRouteText,
} from './navigationPanelHelpers'

const { t } = useI18n()

const props = defineProps({
  navState: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits([
  'toggle-tracking',
  'toggle-voice',
  'toggle-vibration',
  'start-locating',
  'stop-locating',
])

const routeStore = useRouteStore()
const navSession = useNavigationSessionStore()

const endReason = ref('USER_END')

const hasRoutePoints = computed(() => Boolean(routeStore.points.start && routeStore.points.end))
const hasAnyRoute = computed(() => Boolean(routeStore.hasRoute))
const navInfo = computed(() => props.navState?.navInfo?.value || props.navState?.navInfo || null)

const canStartSession = computed(() => hasRoutePoints.value && hasAnyRoute.value)
const canPause = computed(() => navSession.isActive)
const canResume = computed(() => navSession.isPaused)
const canEnd = computed(() => navSession.sessionId && (navSession.isActive || navSession.isPaused))

const coordsText = computed(() => formatCoordsText(props.navState.userLocation))

const offRouteText = computed(() => formatOffRouteText(navSession.offRouteDistanceM))

const hazardText = computed(() => formatHazardText(navSession.hazardWarning))

const levelHintText = computed(() =>
  buildLevelHintText(
    {
      currentLevel: navSession.currentLevel,
      nextLevel: navSession.nextLevel,
      levelTransitionVia: navSession.levelTransitionVia,
    },
    t,
  ),
)
</script>

<template>
  <details data-testid="panel-navigation" class="panel">
    <summary>{{ t('navigation.title') }}</summary>
    <div class="panel-body">
      <div class="section-title">{{ t('navigation.turnByTurn') }}</div>
      <div v-if="navState.userLocation && navInfo" class="hint">
        <div><strong>{{ t('navigation.currentInstruction') }}：</strong>{{ navInfo.currentText || '--' }}</div>
        <div>
          <strong>{{ t('navigation.nextStep') }}：</strong>{{ navInfo.nextText || '--' }}
          <span v-if="navInfo.remainingToNextText && navInfo.remainingToNextText !== '--'">
            （{{ t('navigation.approxAfter', { distance: navInfo.remainingToNextText }) }}）
          </span>
        </div>
        <div>
          <strong>{{ t('navigation.remaining') }}：</strong>{{ navInfo.remainingDistanceText || '--' }}
          <span v-if="navInfo.remainingDurationText && navInfo.remainingDurationText !== '--'">
            （{{ t('navigation.approxTime', { time: navInfo.remainingDurationText }) }}）
          </span>
        </div>
      </div>
      <div v-else class="hint">{{ t('navigation.tipComputeRoute') }}</div>

      <div class="row">
        <button
          data-testid="nav-locating-toggle"
          type="button"
          class="btn"
          :class="{ active: navState.isLocating }"
          @click="navState.isLocating ? emit('stop-locating') : emit('start-locating')"
        >
          {{ navState.isLocating ? t('navigation.stopLocating') : t('navigation.startLocating') }}
        </button>
        <button data-testid="nav-tracking-toggle" type="button" class="btn" @click="emit('toggle-tracking')">
          {{ t('navigation.tracking') }}: {{ navState.trackingMode || 'none' }}
        </button>
      </div>

      <div class="hint">
        {{ t('navigation.location') }}: {{ coordsText }}
        <span v-if="navState.userLocation && Number.isFinite(Number(navState.userLocation.accuracy))">
          (+/-{{ Math.round(Number(navState.userLocation.accuracy)) }}m)
        </span>
      </div>

      <div class="row">
        <label class="row">
          <input
            data-testid="nav-voice-toggle"
            type="checkbox"
            :checked="navState.voiceEnabled"
            @change="emit('toggle-voice', $event.target.checked)"
          />
          <span>{{ t('navigation.voice') }}</span>
        </label>
        <label class="row">
          <input
            data-testid="nav-vibrate-toggle"
            type="checkbox"
            :checked="navState.vibrateEnabled"
            @change="emit('toggle-vibration', $event.target.checked)"
          />
          <span>{{ t('navigation.vibrate') }}</span>
        </label>
      </div>

      <hr class="divider" />

      <div class="section-title">{{ t('navigation.sessionTitle') }}</div>
      <div data-testid="nav-session-status" class="hint">
        Status: {{ navSession.status }}<span v-if="navSession.sessionId"> ({{ navSession.sessionId }})</span>
        <span v-if="navSession.totalLegs > 1">
          | {{ t('navigation.legProgress', { current: navSession.currentLeg + 1, total: navSession.totalLegs }) }}
        </span>
      </div>
      <div v-if="levelHintText" data-testid="nav-level-transition-hint" class="hint">
        {{ levelHintText }}
      </div>
      <div class="hint">
        Off-route: {{ offRouteText }} (hits={{ navSession.offRouteHits }}) | deviation={{ navSession.deviationCount }} |
        reroute={{ navSession.rerouteCount }}
      </div>

      <div v-if="navSession.hazardWarning" class="hint">
        <strong>{{ t('navigation.hazardTitle') }}：</strong>{{ hazardText }}
      </div>
      <div v-if="navSession.hazardWarning" class="row">
        <button
          data-testid="nav-hazard-reroute"
          type="button"
          class="btn"
          :disabled="!navState.userLocation || !navSession.isActive"
          @click="navSession.rerouteFromLocation(navState.userLocation, 'OBSTACLE')"
        >
          {{ t('navigation.avoidHazard') }}
        </button>
        <button type="button" class="btn" @click="navSession.clearHazardWarning()">{{ t('common.close') }}</button>
      </div>

      <div v-if="navSession.waypoints.length > 0 || navSession.isIdle" class="waypoints-section">
        <div class="section-title">{{ t('navigation.waypoints') }}</div>
        <div v-for="(wp, idx) in navSession.waypoints" :key="idx" class="hint waypoint-item">
          <span :class="{ reached: wp.reached }">{{ idx + 1 }}. {{ wp.name || `${wp.lat.toFixed(4)}, ${wp.lng.toFixed(4)}` }}</span>
          <button v-if="navSession.isIdle" type="button" class="btn btn-xs" @click="navSession.removeWaypoint(idx)">{{ t('navigation.removeWaypoint') }}</button>
        </div>
        <div v-if="navSession.isActive && navSession.totalLegs > 1 && navSession.currentLeg < navSession.totalLegs - 1" class="row">
          <button data-testid="nav-advance-leg" type="button" class="btn" @click="navSession.advanceLeg()">
            {{ t('navigation.advanceLeg') }}
          </button>
        </div>
      </div>

      <div class="row">
        <button
          data-testid="nav-start-session"
          type="button"
          class="btn"
          :disabled="!canStartSession"
          @click="navSession.startFromCurrentRoute()"
        >
          {{ t('navigation.startSession') }}
        </button>
        <button data-testid="nav-pause-session" type="button" class="btn" :disabled="!canPause" @click="navSession.pause()">{{ t('navigation.pause') }}</button>
        <button data-testid="nav-resume-session" type="button" class="btn" :disabled="!canResume" @click="navSession.resume()">{{ t('navigation.resume') }}</button>
      </div>

      <div class="row">
        <select data-testid="nav-end-reason" v-model="endReason" class="select">
          <option value="USER_END">USER_END</option>
          <option value="ARRIVED">ARRIVED</option>
          <option value="ERROR">ERROR</option>
        </select>
        <button
          data-testid="nav-end-session"
          type="button"
          class="btn danger"
          :disabled="!canEnd"
          @click="navSession.end(endReason)"
        >
          {{ t('navigation.end') }}
        </button>
        <button
          data-testid="nav-reroute-manual"
          type="button"
          class="btn"
          :disabled="!navSession.sessionId || !navState.userLocation || !navSession.isActive"
          @click="navSession.rerouteFromLocation(navState.userLocation, 'MANUAL')"
        >
          {{ t('navigation.reroute') }}
        </button>
      </div>

      <div v-if="!hasRoutePoints" class="hint">
        {{ t('navigation.tipSetStartEnd') }}
      </div>
      <div v-else-if="!hasAnyRoute" class="hint">
        {{ t('navigation.tipComputeRoute') }}
      </div>
    </div>
  </details>
</template>
