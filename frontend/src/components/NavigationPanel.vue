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
  <!-- 导航面板：逐步导航、定位追踪、会话控制、危险预警 -->
  <details data-testid="panel-navigation" class="nav-panel">
    <summary class="panel-summary">
      <span class="summary-icon">◈</span>
      <span class="summary-label">{{ t('navigation.title') }}</span>
      <span class="summary-arrow"></span>
    </summary>
    <div class="panel-body">
      <!-- 逐步导航信息块 -->
      <div class="nav-info-block">
        <div class="block-header">{{ t('navigation.turnByTurn') }}</div>
        <template v-if="navState.userLocation && navInfo">
          <div class="instruction-card current-inst">
            <span class="inst-badge">NOW</span>
            <span class="inst-text">{{ navInfo.currentText || '--' }}</span>
          </div>
          <div class="instruction-card next-inst">
            <span class="inst-badge next-badge">NEXT</span>
            <span class="inst-text">{{ navInfo.nextText || '--' }}
              <span v-if="navInfo.remainingToNextText && navInfo.remainingToNextText !== '--'" class="inst-sub">
                （{{ t('navigation.approxAfter', { distance: navInfo.remainingToNextText }) }}）
              </span>
            </span>
          </div>
          <div class="remain-row">
            <div class="remain-stat">
              <span class="remain-val">{{ navInfo.remainingDistanceText || '--' }}</span>
              <span class="remain-label">剩余距离</span>
            </div>
            <div class="remain-divider"></div>
            <div class="remain-stat">
              <span class="remain-val">{{ navInfo.remainingDurationText || '--' }}</span>
              <span class="remain-label">预计时间</span>
            </div>
          </div>
        </template>
        <div v-else class="empty-hint">{{ t('navigation.tipComputeRoute') }}</div>
      </div>

      <!-- 定位 & 追踪控制 -->
      <div class="control-row">
        <button
          data-testid="nav-locating-toggle"
          type="button"
          class="ctrl-btn"
          :class="{ active: navState.isLocating }"
          @click="navState.isLocating ? emit('stop-locating') : emit('start-locating')"
        >
          <span class="ctrl-icon">⊙</span>
          {{ navState.isLocating ? t('navigation.stopLocating') : t('navigation.startLocating') }}
        </button>
        <button data-testid="nav-tracking-toggle" type="button" class="ctrl-btn" @click="emit('toggle-tracking')">
          <span class="ctrl-icon">⟳</span>
          {{ t('navigation.tracking') }}: {{ navState.trackingMode || 'none' }}
        </button>
      </div>

      <!-- 位置信息 -->
      <div class="location-info">
        <span class="loc-icon">⊙</span>
        <span class="loc-text">{{ coordsText }}</span>
        <span v-if="navState.userLocation && Number.isFinite(Number(navState.userLocation.accuracy))" class="loc-acc">
          ±{{ Math.round(Number(navState.userLocation.accuracy)) }}m
        </span>
      </div>

      <!-- 语音 & 震动开关 -->
      <div class="toggle-row">
        <label class="toggle-item">
          <input
            data-testid="nav-voice-toggle"
            type="checkbox"
            class="toggle-check"
            :checked="navState.voiceEnabled"
            @change="emit('toggle-voice', $event.target.checked)"
          />
          <span class="toggle-track"></span>
          <span class="toggle-label">{{ t('navigation.voice') }}</span>
        </label>
        <label class="toggle-item">
          <input
            data-testid="nav-vibrate-toggle"
            type="checkbox"
            class="toggle-check"
            :checked="navState.vibrateEnabled"
            @change="emit('toggle-vibration', $event.target.checked)"
          />
          <span class="toggle-track"></span>
          <span class="toggle-label">{{ t('navigation.vibrate') }}</span>
        </label>
      </div>

      <div class="panel-divider"></div>

      <!-- 导航会话控制 -->
      <div class="session-section">
        <div class="block-header">{{ t('navigation.sessionTitle') }}</div>
        <div data-testid="nav-session-status" class="session-status">
          <span class="status-badge" :class="navSession.status?.toLowerCase()">{{ navSession.status || 'IDLE' }}</span>
          <span v-if="navSession.sessionId" class="session-id">{{ navSession.sessionId }}</span>
          <span v-if="navSession.totalLegs > 1" class="leg-info">
            {{ t('navigation.legProgress', { current: navSession.currentLeg + 1, total: navSession.totalLegs }) }}
          </span>
        </div>
        <div v-if="levelHintText" data-testid="nav-level-transition-hint" class="level-hint">
          <span class="level-icon">⇅</span>
          {{ levelHintText }}
        </div>
        <div class="offroute-info">
          <span>偏航：{{ offRouteText }}</span>
          <span class="dot">·</span>
          <span>偏差 {{ navSession.deviationCount }}</span>
          <span class="dot">·</span>
          <span>重算 {{ navSession.rerouteCount }}</span>
        </div>

        <div v-if="navSession.hazardWarning" class="hazard-block">
          <div class="hazard-header">
            <span class="hazard-icon">⚠</span>
            <strong>{{ t('navigation.hazardTitle') }}：</strong>{{ hazardText }}
          </div>
          <div class="hazard-actions">
            <button
              data-testid="nav-hazard-reroute"
              type="button"
              class="hazard-btn avoid"
              :disabled="!navState.userLocation || !navSession.isActive"
              @click="navSession.rerouteFromLocation(navState.userLocation, 'OBSTACLE')"
            >
              {{ t('navigation.avoidHazard') }}
            </button>
            <button type="button" class="hazard-btn dismiss" @click="navSession.clearHazardWarning()">
              {{ t('common.close') }}
            </button>
          </div>
        </div>

        <!-- 途经点列表 -->
        <div v-if="navSession.waypoints.length > 0 || navSession.isIdle" class="waypoints-section">
          <div class="block-header">{{ t('navigation.waypoints') }}</div>
          <div v-for="(wp, idx) in navSession.waypoints" :key="idx" class="waypoint-item">
            <span :class="{ reached: wp.reached }" class="wp-label">{{ idx + 1 }}. {{ wp.name || `${wp.lat.toFixed(4)}, ${wp.lng.toFixed(4)}` }}</span>
            <button v-if="navSession.isIdle" type="button" class="wp-remove-btn" @click="navSession.removeWaypoint(idx)">✕</button>
          </div>
          <div v-if="navSession.isActive && navSession.totalLegs > 1 && navSession.currentLeg < navSession.totalLegs - 1" class="advance-row">
            <button data-testid="nav-advance-leg" type="button" class="ctrl-btn" @click="navSession.advanceLeg()">
              {{ t('navigation.advanceLeg') }}
            </button>
          </div>
        </div>

        <!-- 主控制按钮 -->
        <div class="session-btns">
          <button data-testid="nav-start-session" type="button" class="session-btn start-btn" :disabled="!canStartSession" @click="navSession.startFromCurrentRoute()">
            {{ t('navigation.startSession') }}
          </button>
          <button data-testid="nav-pause-session" type="button" class="session-btn pause-btn" :disabled="!canPause" @click="navSession.pause()">
            {{ t('navigation.pause') }}
          </button>
          <button data-testid="nav-resume-session" type="button" class="session-btn resume-btn" :disabled="!canResume" @click="navSession.resume()">
            {{ t('navigation.resume') }}
          </button>
        </div>

        <!-- 结束 & 重算 -->
        <div class="end-row">
          <select data-testid="nav-end-reason" v-model="endReason" class="end-select">
            <option value="USER_END">USER_END</option>
            <option value="ARRIVED">ARRIVED</option>
            <option value="ERROR">ERROR</option>
          </select>
          <button data-testid="nav-end-session" type="button" class="session-btn end-btn" :disabled="!canEnd" @click="navSession.end(endReason)">
            {{ t('navigation.end') }}
          </button>
          <button data-testid="nav-reroute-manual" type="button" class="session-btn reroute-btn" :disabled="!navSession.sessionId || !navState.userLocation || !navSession.isActive" @click="navSession.rerouteFromLocation(navState.userLocation, 'MANUAL')">
            {{ t('navigation.reroute') }}
          </button>
        </div>
      </div>

      <div v-if="!hasRoutePoints" class="empty-hint">{{ t('navigation.tipSetStartEnd') }}</div>
      <div v-else-if="!hasAnyRoute" class="empty-hint">{{ t('navigation.tipComputeRoute') }}</div>
    </div>
  </details>
</template>

<style scoped>
/* ===== 面板整体 ===== */
.nav-panel {
  background: var(--ui-card, #fff);
  border-radius: 16px;
  box-shadow: 0 4px 24px var(--ui-shadow, rgba(0,0,0,0.10));
  overflow: hidden;
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
}

/* ===== 折叠标题栏 ===== */
.panel-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  cursor: pointer;
  user-select: none;
  list-style: none;
  font-size: 14px;
  font-weight: 700;
  color: var(--ui-ink);
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
}
.panel-summary::-webkit-details-marker { display: none; }
.summary-icon { font-size: 15px; color: var(--ui-accent, #0ea5a4); }
.summary-label { flex: 1; }
.summary-arrow {
  width: 6px; height: 6px;
  border-right: 2px solid var(--ui-muted, #9ca3af);
  border-bottom: 2px solid var(--ui-muted, #9ca3af);
  transform: rotate(45deg);
  transition: transform 0.2s;
}
.nav-panel[open] .summary-arrow { transform: rotate(-135deg); }

/* ===== 内容区 ===== */
.panel-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}
.panel-divider { height: 1px; background: var(--ui-line, #e5e7eb); margin: 2px 0; }

.block-header {
  font-size: 11px;
  font-weight: 700;
  color: var(--ui-muted, #9ca3af);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 8px;
}

/* ===== 导航信息块 ===== */
.nav-info-block {
  border: 1px solid var(--ui-line, #e5e7eb);
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.instruction-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
}
.current-inst {
  background: rgba(14, 165, 164, 0.06);
  border: 1px solid rgba(14, 165, 164, 0.15);
}
.next-inst {
  background: var(--ui-bg, #f9fafb);
  border: 1px solid var(--ui-line, #e5e7eb);
}
.inst-badge {
  font-size: 10px; font-weight: 800;
  padding: 2px 6px; border-radius: 4px;
  background: var(--ui-accent, #0ea5a4); color: #fff;
  letter-spacing: 0.04em; flex-shrink: 0; margin-top: 1px;
}
.next-badge { background: var(--ui-muted, #9ca3af); }
.inst-text { font-size: 13px; color: var(--ui-ink); line-height: 1.5; flex: 1; }
.inst-sub { font-size: 12px; color: var(--ui-muted, #9ca3af); }
.remain-row {
  display: flex; align-items: center; gap: 16px;
  padding-top: 8px; border-top: 1px solid var(--ui-line, #e5e7eb);
}
.remain-stat { display: flex; flex-direction: column; gap: 2px; }
.remain-val { font-size: 16px; font-weight: 800; color: var(--ui-ink); letter-spacing: -0.02em; line-height: 1.2; }
.remain-label { font-size: 11px; color: var(--ui-muted, #9ca3af); }
.remain-divider { width: 1px; height: 28px; background: var(--ui-line, #e5e7eb); }

/* ===== 定位追踪 ===== */
.control-row { display: flex; gap: 8px; }
.ctrl-btn {
  flex: 1;
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 12px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 10px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.ctrl-btn.active {
  border-color: var(--ui-accent, #0ea5a4);
  background: rgba(14,165,164,0.06);
  color: var(--ui-accent, #0ea5a4);
}
.ctrl-icon { font-size: 14px; }

/* ===== 位置信息 ===== */
.location-info {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: var(--ui-muted, #9ca3af);
  padding: 6px 10px;
  background: var(--ui-bg, #f9fafb);
  border-radius: 8px;
}
.loc-icon { font-size: 13px; color: var(--ui-accent, #0ea5a4); }
.loc-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.loc-acc {
  font-size: 11px; font-weight: 600;
  color: var(--ui-accent, #0ea5a4);
  flex-shrink: 0;
}

/* ===== 语音/震动开关 ===== */
.toggle-row { display: flex; gap: 16px; }
.toggle-item {
  display: flex; align-items: center; gap: 8px;
  cursor: pointer; font-size: 13px; color: var(--ui-ink);
}
.toggle-check { display: none; }
.toggle-track {
  width: 36px; height: 20px;
  border-radius: 999px;
  background: var(--ui-line, #e5e7eb);
  position: relative;
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-track::after {
  content: '';
  position: absolute;
  width: 14px; height: 14px;
  border-radius: 50%;
  background: #fff;
  top: 3px; left: 3px;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}
.toggle-check:checked + .toggle-track {
  background: var(--ui-accent, #0ea5a4);
}
.toggle-check:checked + .toggle-track::after {
  transform: translateX(16px);
}
.toggle-label { font-size: 13px; color: var(--ui-ink); }

/* ===== 会话区 ===== */
.session-section { display: flex; flex-direction: column; gap: 10px; }
.session-status { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

.status-badge {
  padding: 2px 8px; border-radius: 999px;
  font-size: 11px; font-weight: 700;
  letter-spacing: 0.04em;
  background: var(--ui-line, #e5e7eb);
  color: var(--ui-muted, #6b7280);
}
.status-badge.active { background: rgba(34,197,94,0.12); color: #16a34a; }
.status-badge.paused { background: rgba(249,115,22,0.1); color: #f97316; }
.status-badge.idle   { background: var(--ui-line, #e5e7eb); color: var(--ui-muted, #6b7280); }

.session-id { font-size: 11px; color: var(--ui-muted, #9ca3af); font-family: monospace; }
.leg-info   { font-size: 12px; color: var(--ui-accent, #0ea5a4); font-weight: 600; }

/* ===== 楼层切换提示 ===== */
.level-hint {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px; border-radius: 8px;
  background: rgba(14,165,164,0.06);
  border: 1px solid rgba(14,165,164,0.15);
  font-size: 13px; color: var(--ui-ink);
}
.level-icon { font-size: 16px; color: var(--ui-accent, #0ea5a4); }

/* ===== 偏航信息 ===== */
.offroute-info {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: var(--ui-muted, #9ca3af);
}
.dot { opacity: 0.4; }

/* ===== 危险预警 ===== */
.hazard-block {
  border-radius: 10px;
  background: rgba(249,115,22,0.06);
  border: 1px solid rgba(249,115,22,0.2);
  padding: 10px 12px;
  display: flex; flex-direction: column; gap: 8px;
}
.hazard-header {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; color: var(--ui-ink);
}
.hazard-icon { font-size: 16px; color: #f97316; }
.hazard-actions { display: flex; gap: 8px; }
.hazard-btn {
  padding: 5px 10px; border-radius: 8px;
  font-size: 12px; font-weight: 600;
  cursor: pointer; border: 1.5px solid;
  transition: all 0.15s;
}
.hazard-btn.avoid {
  border-color: rgba(249,115,22,0.3);
  background: rgba(249,115,22,0.08);
  color: #f97316;
}
.hazard-btn.avoid:hover { background: rgba(249,115,22,0.16); }
.hazard-btn.dismiss {
  border-color: var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
}
.hazard-btn.dismiss:hover { background: var(--ui-line, #e5e7eb); }
.hazard-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* ===== 途经点 ===== */
.waypoints-section { display: flex; flex-direction: column; gap: 6px; }
.waypoint-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px; border-radius: 8px;
  background: var(--ui-bg, #f9fafb);
  border: 1px solid var(--ui-line, #e5e7eb);
}
.wp-label { flex: 1; font-size: 13px; color: var(--ui-ink); }
.wp-label.reached { text-decoration: line-through; color: var(--ui-muted, #9ca3af); }
.wp-remove-btn {
  width: 20px; height: 20px; border-radius: 50%;
  border: none; background: var(--ui-line, #e5e7eb);
  color: var(--ui-muted, #6b7280); font-size: 11px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: background 0.15s;
}
.wp-remove-btn:hover { background: rgba(239,68,68,0.15); color: #dc2626; }
.advance-row { margin-top: 4px; }

/* ===== 主控制按钮 ===== */
.session-btns { display: flex; gap: 6px; }
.session-btn {
  flex: 1; padding: 8px 10px;
  border-radius: 9px; border: 1.5px solid;
  font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
}
.start-btn  { border-color: rgba(34,197,94,0.3);   background: rgba(34,197,94,0.08);  color: #16a34a; }
.pause-btn  { border-color: rgba(249,115,22,0.3);  background: rgba(249,115,22,0.08); color: #f97316; }
.resume-btn { border-color: rgba(14,165,164,0.3);  background: rgba(14,165,164,0.08); color: var(--ui-accent, #0ea5a4); }
.end-btn    { border-color: rgba(239,68,68,0.3);   background: rgba(239,68,68,0.08);  color: #dc2626; }
.reroute-btn{ border-color: var(--ui-line,#e5e7eb);background: var(--ui-bg,#f9fafb);  color: var(--ui-ink); }
.session-btn:hover:not(:disabled) { filter: brightness(0.95); }
.session-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* ===== 结束行 ===== */
.end-row { display: flex; gap: 6px; align-items: center; }
.end-select {
  padding: 7px 10px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px;
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink);
  font-size: 12px;
  outline: none;
  cursor: pointer;
}

/* ===== 空提示 ===== */
.empty-hint {
  font-size: 13px;
  color: var(--ui-muted, #9ca3af);
  padding: 8px 0;
  text-align: center;
}
</style>

