<script setup>
import { computed, watch, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

// Stores
import { useToastStore } from '../stores/useToastStore'
import { useMapStore } from '../stores/useMapStore'
import { useRouteStore } from '../stores/useRouteStore'
import { useSearchStore } from '../stores/useSearchStore'
import { useNavStore } from '../stores/useNavStore'
import { useFavoritesStore } from '../stores/useFavoritesStore'
import { useSessionStore } from '../stores/useSessionStore'
import { useAccessibilityProfileStore } from '../stores/useAccessibilityProfileStore'
import { useHistoryStore } from '../stores/useHistoryStore'
import { useObstacleStore } from '../stores/useObstacleStore'
import { useNavigationSessionStore } from '../stores/useNavigationSessionStore'

// Components
import MapCanvas from './MapCanvas.vue'
import SearchPanel from './SearchPanel.vue'
import RoutePanel from './RoutePanel.vue'
import NavigationPanel from './NavigationPanel.vue'
import RouteReportPanel from './RouteReportPanel.vue'
import FavoritesPanel from './FavoritesPanel.vue'
import MyReportsPanel from './MyReportsPanel.vue'
import SettingsPanel from './SettingsPanel.vue'
import ObstacleReviewPanel from './ObstacleReviewPanel.vue'
import QualityPanel from './QualityPanel.vue'
import HistoryPanel from './HistoryPanel.vue'
import ReportObstacleDialog from './ReportObstacleDialog.vue'
import ToastStack from './ToastStack.vue'

// Utils / Composables
import { createBaseMapStyleController } from '../composables/useBaseMapStyle'
import { loadMapDeps } from '../composables/mapDeps'
import { wgs84ToGcj02, gcj02ToWgs84 } from '../utils/coordTransform'
import { logger } from '../utils/logger'
import {
  buildBaseMapOptions,
  buildDisplayGeoJson,
  buildExportReportContent,
  buildHistoryPayload,
  buildShareQrSrc,
  buildWaypointPayload,
  isEnvTruthy,
  resolveObstacleSuccessToast,
  resolveRouteModeFromProfile,
} from './accessibleMapHelpers'

const toast = useToastStore()
const mapStore = useMapStore()
const routeStore = useRouteStore()
const searchStore = useSearchStore()
const navStore = useNavStore()
const favoritesStore = useFavoritesStore()
const sessionStore = useSessionStore()
const accessibilityProfileStore = useAccessibilityProfileStore()
const historyStore = useHistoryStore()
const obstacleStore = useObstacleStore()
const navigationSessionStore = useNavigationSessionStore()
sessionStore.init()

const { t, locale } = useI18n()

const canUserFeatures = computed(() => sessionStore.isAuthenticated)
const canAdminFeatures = computed(() => sessionStore.isAdmin)
const voicePolicySaving = ref(false)

// --- Map References & Setup ---
const mapRef = ref(null)
const mapEl = ref(null)
let maplibreInstance = null
let startMarker = null
let endMarker = null
let navLocationMarker = null

const mapDisabled = isEnvTruthy(import.meta.env.VITE_DISABLE_MAP)
const apiBaseUrl = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

// Handlers for MapCanvas
function handleMapReady(el) {
  mapEl.value = el
  ensureMapCreated()
}

// --- Base Map Style Controller ---
const baseMapController = createBaseMapStyleController({
  baseMapState: mapStore.baseMapState,
  getMap: () => mapRef.value,
  apiBaseUrl,
  maptilerKey: import.meta.env.VITE_MAPTILER_KEY,
  maptilerBaseKeys: [
    'streets',
    'basic',
    'bright',
    'dataviz',
    'outdoor',
    'satellite',
    'topo',
    'toner',
    'winter',
    'hybrid',
  ],
})

// Options for Settings Panel
const baseMapOptions = computed(() => {
  // Recompute labels when locale changes.
  void locale.value
  return buildBaseMapOptions(t, Boolean(import.meta.env.VITE_MAPTILER_KEY))
})

function applyBaseMapSelection(key) {
  baseMapController.setBaseMapStyle(key) 
}

function handleClearPoint(which) {
  if (which === 'start') {
    routeStore.setPoint('start', null)
    searchStore.setWhich('start')
    return
  }
  if (which === 'end') {
    routeStore.setPoint('end', null)
    searchStore.setWhich('end')
    return
  }
  routeStore.clearPoints()
}

// 处理历史记录选择
function handleSelectHistory(item) {
  routeStore.setPoint('start', item.start)
  routeStore.setPoint('end', item.end)
  routeStore.routeState.activeMode = item.mode === 'wheel' ? 'wheel' : 'walk'
  toast.push(t('toast.historyRouteLoaded'))
}

function handleAddWaypoint(item) {
  const resolved = buildWaypointPayload(item, t('map.pickedPointName'))
  if (!resolved) return

  navigationSessionStore.addWaypoint(resolved.waypoint)
  toast.push(t('toast.waypointAdded', { label: resolved.label }))
}

function applyAccessibilityProfileToRouteMode(profile) {
  const nextMode = resolveRouteModeFromProfile(profile)
  routeStore.setActiveMode(nextMode)
}

async function loadAccessibilityProfile() {
  if (!sessionStore.isAuthenticated) {
    accessibilityProfileStore.resetProfile()
    return
  }

  try {
    const profile = await accessibilityProfileStore.fetchProfile()
    applyAccessibilityProfileToRouteMode(profile)
  } catch (e) {
    toast.push(t('toast.accessibilityProfileLoadFailed', { message: e?.message || String(e) }))
  }
}

async function saveAccessibilityProfile(payload) {
  if (!sessionStore.isAuthenticated) {
    toast.push(t('toast.loginRequired'))
    return
  }
  try {
    const profile = await accessibilityProfileStore.saveProfile(payload)
    applyAccessibilityProfileToRouteMode(profile)
    toast.push(t('toast.accessibilityProfileSaved'))
  } catch (e) {
    toast.push(t('toast.accessibilityProfileSaveFailed', { message: e?.message || String(e) }))
  }
}

async function saveVoicePolicy(payload) {
  if (!sessionStore.isAuthenticated) {
    toast.push(t('toast.loginRequired'))
    return
  }

  voicePolicySaving.value = true
  try {
    await navStore.saveVoicePolicy(payload)
    toast.push(t('toast.voicePolicySaved'))
  } catch (e) {
    toast.push(t('toast.voicePolicySaveFailed', { message: e?.message || String(e) }))
  } finally {
    voicePolicySaving.value = false
  }
}

// --- Coordinate Transforms ---
const useGcj = computed(() => ['gaode', 'gaode-satellite'].includes(mapStore.baseMapState.current))
function toDisplayCoords(lng, lat) {
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) return {lng, lat}
    return useGcj.value ? wgs84ToGcj02(lng, lat) : { lng, lat }
}
function toWgsCoords(lng, lat) {
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) return {lng, lat}
    return useGcj.value ? gcj02ToWgs84(lng, lat) : { lng, lat }
}

// --- Map Initialization ---
async function ensureMapCreated() {
    if (mapDisabled || mapRef.value || !mapEl.value) return
    
    const { maplibregl } = await loadMapDeps()
    maplibreInstance = maplibregl

    const initialStyleKey = mapStore.baseMapState.current || baseMapController.resolveSafeBaseMap()
    // Center on GBUC Jianggao approx
    const center = toDisplayCoords(113.2025, 23.275)

    const map = new maplibregl.Map({
        container: mapEl.value,
        style: baseMapController.getBaseMapStyle(initialStyleKey),
        center: [center.lng, center.lat],
        zoom: 16,
        preserveDrawingBuffer: true
    })

    map.on('error', baseMapController.handleBaseMapError)
    map.on('click', handleMapClick)
    map.on('style.load', () => {
        syncRouteLayers()
        updateMarkers()
    })

    mapRef.value = map
    mapStore.baseMapState.current = initialStyleKey
    updateMarkers()
    updateNavLocationMarker()
}

// --- Map Interaction ---
function handleMapClick(e) {
  const { lng, lat } = toWgsCoords(e.lngLat.lng, e.lngLat.lat)
  
  if (reportDialogState.value.pickingLocation) {
    reportDialogState.value.lat = lat
    reportDialogState.value.lng = lng
    reportDialogState.value.pickingLocation = false
    reportDialogState.value.visible = true
    toast.push(t('toast.locationSelected')) // Fixed: using store push
    return
  }

  // Click to set start/end
  const which = searchStore.searchState.which
  
  if (which === 'start') {
      routeStore.setPoint('start', { lng, lat, name: t('map.pickedPointName') })
      toast.push(t('toast.startPointSet'))
      searchStore.setWhich('end')
  } else {
      routeStore.setPoint('end', { lng, lat, name: t('map.pickedPointName') })
      toast.push(t('toast.endPointSet'))
  }
}

// --- Markers ---
function updateMarkers() {
  if (!mapRef.value || !maplibreInstance) return

  const updateMarker = (markerRef, point, color) => {
      let m = markerRef
      if (point) {
          const { lng, lat } = toDisplayCoords(point.lng, point.lat)
          if (!m) {
              m = new maplibreInstance.Marker({ color })
                  .setLngLat([lng, lat])
                  .addTo(mapRef.value)
          } else {
              m.setLngLat([lng, lat])
          }
      } else if (m) {
          m.remove()
          m = null
      }
      return m
  }

  startMarker = updateMarker(startMarker, routeStore.points.start, '#00AA00') 
  endMarker = updateMarker(endMarker, routeStore.points.end, '#FF0000')   
}

watch(() => routeStore.points, () => {
    updateMarkers()
    routeStore.computeRoutes()
}, { deep: true })

// 当路线计算成功时记录历史
watch(() => routeStore.activeRoute, (route) => {
    const payload = buildHistoryPayload(route, routeStore.points, routeStore.routeState.activeMode)
    if (payload) {
        historyStore.addHistory(payload)
    }
})


// --- Route Layers ---
function syncRouteLayers() {
    const map = mapRef.value
    if (!map) return
    if (!map.isStyleLoaded()) return

    const id = 'route-layer'

    if (map.getSource(id)) {
        const src = map.getSource(id)
        // If we have a route, update data
        if (routeStore.activeRoute && src.setData) {
             const displayGeoJson = buildDisplayGeoJson(routeStore.activeRoute, useGcj.value, wgs84ToGcj02)
             src.setData(displayGeoJson)
             return
        }
        // If no route, clear
        if (!routeStore.activeRoute && src.setData) {
            src.setData({ type: 'FeatureCollection', features: [] })
            return
        }
    } else if (routeStore.activeRoute) {
        // Create layer if not exists (and route exists)
        // Recurse to handle creation logic cleanly
        // Or just map.addSource...
    }
    
    // Fallback / Initial Create
    if (!map.getSource(id) && routeStore.activeRoute) {
         const displayGeoJson = buildDisplayGeoJson(routeStore.activeRoute, useGcj.value, wgs84ToGcj02)
        map.addSource(id, {
            type: 'geojson',
            data: displayGeoJson
        })
        map.addLayer({
            id,
            type: 'line',
            source: id,
            layout: { 'line-join': 'round', 'line-cap': 'round' },
            paint: {
                'line-color': '#3b82f6',
                'line-width': 6,
                'line-opacity': 0.8
            }
        })
    }
    
    // If source exists but no route, we cleared it in the first block
    // If source does not exist and no route, do nothing
}

watch(() => routeStore.activeRoute, syncRouteLayers)
watch(() => useGcj.value, () => {
    updateMarkers()
    updateNavLocationMarker()
    syncRouteLayers()
})


function handleExportImage() {
    const filename = `route-report-${Date.now()}.png`
    const download = (url) => {
        const a = document.createElement('a')
        a.href = url
        a.download = filename
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
    }

    try {
        // Prefer exporting the map canvas when available.
        if (mapRef.value && typeof mapRef.value.getCanvas === 'function') {
            const canvas = mapRef.value.getCanvas()
            download(canvas.toDataURL('image/png'))
            toast.push(t('toast.exportStarted'))
            return
        }

        // Fallback (e.g. E2E/headless with map disabled): generate a lightweight PNG from route report text.
        const w = 1080
        const h = 720
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        const ctx = canvas.getContext('2d')
        if (!ctx) {
            toast.push(t('toast.exportCanvasUnavailable'))
            return
        }

        ctx.fillStyle = '#ffffff'
        ctx.fillRect(0, 0, w, h)

        const startName = routeStore.points.start?.name || t('map.startPoint')
        const endName = routeStore.points.end?.name || t('map.endPoint')
        const summary = routeStore.activeRoute?.summary || routeStore.activeRoute || null
        const { title, startLine, endLine, distanceLine, durationLine, riskLine } = buildExportReportContent({
            activeMode: routeStore.routeState.activeMode,
            startName,
            endName,
            summary,
            translate: t,
        })

        ctx.fillStyle = '#0f172a'
        ctx.font = 'bold 44px sans-serif'
        ctx.fillText(title, 60, 90)

        ctx.font = '28px sans-serif'
        ctx.fillText(startLine, 60, 160)
        ctx.fillText(endLine, 60, 210)

        ctx.fillText(distanceLine, 60, 280)
        ctx.fillText(durationLine, 60, 330)
        ctx.fillText(riskLine, 60, 380)

        ctx.fillStyle = '#64748b'
        ctx.font = '22px sans-serif'
        ctx.fillText(`${t('report.generatedAt')}: ${new Date().toLocaleString()}`, 60, 460)

        download(canvas.toDataURL('image/png'))
        toast.push(t('toast.exportStarted'))
    } catch (e) {
        logger.error(e)
        const msg = e?.message || String(e)
        toast.push(`${t('toast.exportFailed')}: ${msg}`)
    }
}

// --- Sharing ---
const shareQrVisible = ref(false)
const shareQrSrc = ref('')

function handleCopyLink() {
    const url = window.location.href
    navigator.clipboard.writeText(url).then(() => {
        toast.push(t('toast.linkCopied'))
    }).catch(e => {
        logger.error(e)
        toast.push(t('toast.copyFailed'))
    })
}

function handleOpenQr() {
    shareQrVisible.value = !shareQrVisible.value
    if (shareQrVisible.value) {
        shareQrSrc.value = buildShareQrSrc(window.location.href)
    }
}


// --- Navigation Marker ---
function updateNavLocationMarker() {
    const loc = navStore.userLocation
    if (!mapRef.value || !maplibreInstance) return

    if (loc) {
        const { lng, lat } = toDisplayCoords(loc.lng, loc.lat)
        if (!navLocationMarker) {
            const el = document.createElement('div')
            el.className = 'nav-marker' 
            el.style.width = '16px'; el.style.height = '16px'; 
            el.style.backgroundColor = 'blue'; el.style.borderRadius = '50%';
            el.style.border = '2px solid white';
            
            navLocationMarker = new maplibreInstance.Marker({ element: el })
                .setLngLat([lng, lat])
                .addTo(mapRef.value)
        } else {
            navLocationMarker.setLngLat([lng, lat])
        }

        if (navStore.trackingMode === 'follow') {
            mapRef.value.panTo([lng, lat])
        }
    } else if (navLocationMarker) {
        navLocationMarker.remove()
        navLocationMarker = null
    }
}
watch(() => navStore.userLocation, updateNavLocationMarker, { deep: true })
watch(() => navStore.trackingMode, () => {
    if (navStore.trackingMode === 'follow' && navStore.userLocation) {
        updateNavLocationMarker()
    }
})


// --- Quality Panel State ---
const qualityState = ref({
    report: null,
    history: [],
    loading: false
})
const issueFilterOptions = ref([])

// --- Reporting Dialog Logic ---
const reportDialogState = ref({
    visible: false,
    lat: 0,
    lng: 0,
    pickingLocation: false
})
// RouteReportPanel local state mimicking
const reportPanelVisible = ref(false)

function handleObstacleReportSuccess(report) {
  const status = String(report?.status || '').toUpperCase()
  toast.push(resolveObstacleSuccessToast(status, t))
  obstacleStore.fetchMyReports(obstacleStore.myReportsState.status)
  if (canAdminFeatures.value) {
    obstacleStore.fetchAdminReports(obstacleStore.adminReportsState.status)
  }
}

// --- Lifecycle ---
onMounted(() => {
    searchStore.loadHistory()
    searchStore.loadCampusData() 
    favoritesStore.loadFavorites()
    void navStore.loadVoicePolicy()
    void navigationSessionStore.restoreByResumeToken()
    void loadAccessibilityProfile()
})

watch(canUserFeatures, (enabled) => {
    if (enabled) {
        void navStore.loadVoicePolicy()
        void loadAccessibilityProfile()
        return
    }
    accessibilityProfileStore.resetProfile()
})

</script>

<template>
  <div class="accessible-map-container h-full w-full relative flex flex-col overflow-hidden">
    <ToastStack :text="toast.text" />

    <div class="map-panel-stack z-20 absolute top-2 left-2 flex flex-col gap-2 max-h-full pointer-events-none">
        <SearchPanel 
            class="pointer-events-auto"
            :search-state="searchStore.searchState"
            :search-results="searchStore.searchResults"
            :search-history="searchStore.searchHistory"
            :campus-items="searchStore.campusItems"
            :category-options="searchStore.categoryOptions"
            :loading="searchStore.campusDataState.loading"
            :error="searchStore.campusDataState.error"
            :points="routeStore.points"
            @update:query="searchStore.setQuery"
            @update:which="searchStore.setWhich"
            @toggle-category="searchStore.toggleCategory"
            @select-result="searchStore.applySearchResult"
            @select-waypoint="handleAddWaypoint"
            @clear-history="searchStore.clearHistory"
            @clear-point="handleClearPoint"
        />

        <RoutePanel
            v-if="routeStore.hasRoute"
            class="pointer-events-auto"
            :has-route="routeStore.hasRoute"
            :loading="routeStore.routeState.loading"
            :error="routeStore.routeState.error"
            :walk-summary="routeStore.routeState.walk ? routeStore.routeState.walk.summary : null"
            :wheel-summary="routeStore.routeState.wheel ? routeStore.routeState.wheel.summary : null"
            :active-mode="routeStore.routeState.activeMode"
            :strategy="routeStore.strategy"
            :strategy-weights="routeStore.strategyWeights"
            @select-mode="(m) => { routeStore.routeState.activeMode = m }"
            @update:strategy="(s) => { routeStore.setStrategy(s); routeStore.computeRoutes() }"
            @update:strategy-weights="(w) => { routeStore.setStrategyWeights(w); routeStore.computeRoutes() }"
        />

        <RouteReportPanel
           v-if="routeStore.hasRoute"
           class="pointer-events-auto"
           :report-visible="reportPanelVisible"
           :points="routeStore.points"
           :active-route="routeStore.activeRoute"
           :active-mode="routeStore.routeState.activeMode"
           :can-export="true"
           :can-report="canUserFeatures"
           @toggle-report="reportPanelVisible = !reportPanelVisible"
           @export-image="handleExportImage"
           @toggle-report-issue="() => { 
                if (!canUserFeatures.value) {
                    toast.push(t('report.loginToUnlockReport'))
                    return
                }
                reportDialogState.pickingLocation = true; 
                toast.push(t('map.mapClickToSelect')); 
           }"
        />

        <NavigationPanel
           class="pointer-events-auto"
           :nav-state="{
               isLocating: navStore.isLocating,
               userLocation: navStore.userLocation,
               trackingMode: navStore.trackingMode,
               navInfo: navStore.navInfo,
               voiceEnabled: navStore.voiceEnabled,
               vibrateEnabled: navStore.vibrateEnabled
           }"
           @toggle-tracking="navStore.toggleTracking"
           @toggle-voice="navStore.toggleVoice"
           @toggle-vibration="navStore.toggleVibration"
           @start-locating="navStore.startLocationWatch"
           @stop-locating="navStore.stopLocationWatch"
        />

        <FavoritesPanel 
              class="pointer-events-auto" 
              :favorite-state="favoritesStore.favoriteState"
              :filtered-favorites="favoritesStore.filteredFavorites"
             @update-field="(payload) => favoritesStore.favoriteState[payload.key] = payload.value"
             @add-favorite="favoritesStore.addFavorite"
             @remove-favorite="favoritesStore.removeFavorite"
             @export-favorites="favoritesStore.doExport"
             @import-favorites="favoritesStore.doImport"
        />

        <MyReportsPanel
            v-if="canUserFeatures"
            class="pointer-events-auto"
            :enabled="canUserFeatures"
        />

        <ObstacleReviewPanel
            v-if="canAdminFeatures"
            class="pointer-events-auto"
            :enabled="canAdminFeatures"
        />

        <SettingsPanel 
            class="pointer-events-auto"
            :base-map-state="mapStore.baseMapState"
            :base-map-options="baseMapOptions"
            :campus-only="mapStore.campusOnly"
            :share-qr-visible="shareQrVisible"
            :share-qr-src="shareQrSrc"
            :accessibility-profile="accessibilityProfileStore.profile"
            :accessibility-loading="accessibilityProfileStore.loading"
            :accessibility-saving="accessibilityProfileStore.saving"
            :voice-policy="navStore.voicePolicy"
            :voice-saving="voicePolicySaving"
            @change-basemap="applyBaseMapSelection"
            @toggle-campus-only="mapStore.setCampusOnly"
            @copy-link="handleCopyLink"
            @open-qr="handleOpenQr"
            @save-accessibility-profile="saveAccessibilityProfile"
            @save-voice-policy="saveVoicePolicy"
        />

        <QualityPanel
            v-if="canAdminFeatures"
            class="pointer-events-auto"
            :quality-state="qualityState"
            :issue-filter-options="issueFilterOptions"
            :has-admin="canAdminFeatures"
        />

        <HistoryPanel
            class="pointer-events-auto"
            @select-history="handleSelectHistory"
        />
    </div>

    <div class="flex-1 relative z-0">
        <MapCanvas @ready="handleMapReady" />
    </div>

    <ReportObstacleDialog
        v-if="reportDialogState.visible"
        :visible="reportDialogState.visible"
        :lat="reportDialogState.lat"
        :lng="reportDialogState.lng" 
        @close="reportDialogState.visible = false"
        @success="handleObstacleReportSuccess"
        @pick-location="() => { 
            reportDialogState.visible = false; 
            reportDialogState.pickingLocation = true;
            toast.push(t('map.mapClickToSelect'));
        }"
    />
  </div>
</template>

<style scoped>
.accessible-map-container {
  position: relative;
  height: 100%;
  width: 100%;
  background-color: #f3f4f6;
  color: var(--ui-ink);
  overflow: hidden;
}
</style>
