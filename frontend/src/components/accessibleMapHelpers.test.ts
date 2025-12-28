import { describe, expect, it } from 'vitest'

import {
  buildBaseMapOptions,
  buildDisplayGeoJson,
  buildExportReportContent,
  buildHistoryPayload,
  buildShareQrSrc,
  buildWaypointPayload,
  extractRouteSummaryMetrics,
  formatRouteMetrics,
  isEnvTruthy,
  resolveObstacleSuccessToast,
  resolveRouteModeFromProfile,
} from './accessibleMapHelpers'

describe('accessibleMapHelpers', () => {
  const t = (key: string, params?: Record<string, unknown>) => {
    if (!params) return key
    return `${key}:${Object.entries(params).map(([k, v]) => `${k}=${String(v)}`).join(',')}`
  }

  it('detects truthy env values', () => {
    expect(isEnvTruthy('1')).toBe(true)
    expect(isEnvTruthy('true')).toBe(true)
    expect(isEnvTruthy('off')).toBe(false)
    expect(isEnvTruthy('')).toBe(false)
  })

  it('builds base map options with optional MapTiler entries', () => {
    expect(buildBaseMapOptions(t, false)).toHaveLength(3)
    expect(buildBaseMapOptions(t, true)).toHaveLength(6)
  })

  it('resolves route mode from accessibility profile', () => {
    expect(resolveRouteModeFromProfile({ mobilityMode: 'WHEELCHAIR' })).toBe('wheel')
    expect(resolveRouteModeFromProfile({ mobilityMode: 'walk' })).toBe('walk')
    expect(resolveRouteModeFromProfile(null)).toBe('walk')
  })

  it('builds waypoint payload and labels', () => {
    expect(buildWaypointPayload({ lat: 23.27, lng: 113.2, name: 'Library' }, 'picked')).toEqual({
      waypoint: {
        lat: 23.27,
        lng: 113.2,
        name: 'Library',
        reached: false,
      },
      label: 'Library',
    })
    expect(buildWaypointPayload({ lat: 23.27, lng: 113.2 }, 'picked')?.label).toBe('23.2700,113.2000')
    expect(buildWaypointPayload({ lat: 'bad', lng: 113.2 }, 'picked')).toBeNull()
  })

  it('resolves obstacle success toasts', () => {
    expect(resolveObstacleSuccessToast('PENDING', t)).toBe('toast.obstaclePendingReview')
    expect(resolveObstacleSuccessToast('APPROVED', t)).toBe('toast.obstacleReportSuccessWithStatus:status=APPROVED')
    expect(resolveObstacleSuccessToast('', t)).toBe('obstacle.submitSuccess')
  })

  it('formats route metrics and extracts summary fields', () => {
    expect(formatRouteMetrics(1234, 125)).toEqual({
      distanceText: '1.23 km',
      durationText: '2 min',
    })
    expect(extractRouteSummaryMetrics({ distanceM: 10, durationSec: 20, riskCount: 3 })).toEqual({
      distanceM: 10,
      durationSec: 20,
      riskCount: 3,
    })
  })

  it('builds history payload only when route summary and points exist', () => {
    expect(
      buildHistoryPayload(
        { summary: { distanceM: 10, durationSec: 20 } },
        {
          start: { lng: 1, lat: 2, name: 'A' },
          end: { lng: 3, lat: 4, name: 'B' },
        },
        'walk',
      ),
    ).toEqual({
      start: { lng: 1, lat: 2, name: 'A' },
      end: { lng: 3, lat: 4, name: 'B' },
      mode: 'walk',
      distanceM: 10,
      durationSec: 20,
    })
    expect(buildHistoryPayload(null, { start: null, end: null }, 'walk')).toBeNull()
  })

  it('transforms route geojson for display when gcj mode is enabled', () => {
    const route = {
      features: [
        {
          geometry: {
            coordinates: [
              [113.2, 23.27],
              [113.21, 23.28],
            ],
          },
        },
      ],
    }
    const transformed = buildDisplayGeoJson(route, true, (lng, lat) => ({ lng: lng + 1, lat: lat + 1 }))
    expect(transformed?.features?.[0]?.geometry?.coordinates).toEqual([
      [114.2, 24.27],
      [114.21, 24.28],
    ])
    expect(buildDisplayGeoJson(route, false, (lng, lat) => ({ lng: lng + 1, lat: lat + 1 }))?.features?.[0]?.geometry?.coordinates).toEqual([
      [113.2, 23.27],
      [113.21, 23.28],
    ])
  })

  it('builds share qr src and export report content', () => {
    expect(buildShareQrSrc('http://localhost:5173/?a=1')).toContain(encodeURIComponent('http://localhost:5173/?a=1'))
    expect(
      buildExportReportContent({
        activeMode: 'wheel',
        startName: 'A',
        endName: 'B',
        summary: { distanceM: 1200, durationSec: 130, riskCount: 2 },
        translate: t,
      }),
    ).toMatchObject({
      title: 'report.routeReportTitle:mode=route.wheelchair',
      startLine: 'map.startPoint: A',
      endLine: 'map.endPoint: B',
      distanceLine: 'route.distance: 1.20 km',
      durationLine: 'route.duration: 2 min',
      riskLine: 'route.riskCount: 2',
    })
  })
})
