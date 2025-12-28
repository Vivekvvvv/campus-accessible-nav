import { describe, expect, it, vi } from 'vitest'

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
  parseTimeToMinutes,
  routeSignature,
  unwrapApiPayload,
} from './navStoreHelpers'

describe('navStoreHelpers', () => {
  it('clamps values into range', () => {
    expect(clamp(10, 0, 5, 3)).toBe(5)
    expect(clamp(-1, 0, 5, 3)).toBe(0)
    expect(clamp('bad', 0, 5, 3)).toBe(3)
  })

  it('parses time strings', () => {
    expect(parseTimeToMinutes('08:30')).toBe(510)
    expect(parseTimeToMinutes('')).toBeNull()
    expect(parseTimeToMinutes('25:00')).toBeNull()
  })

  it('detects quiet hours across midnight', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-18T23:30:00'))

    expect(
      isInQuietHours({
        ...DEFAULT_VOICE_POLICY,
        quietHoursStart: '22:00',
        quietHoursEnd: '06:00',
      })
    ).toBe(true)

    vi.useRealTimers()
  })

  it('normalizes voice policy values', () => {
    expect(
      normalizeVoicePolicy({
        preTurnM: 999,
        preArrivalM: -5,
        announceIntervalM: 40,
        quietHoursStart: ' 08:00 ',
        quietHoursEnd: '09:00',
        vibrateEnabled: false,
      })
    ).toEqual({
      preTurnM: 500,
      preArrivalM: 0,
      announceIntervalM: 40,
      quietHoursStart: '08:00',
      quietHoursEnd: '09:00',
      vibrateEnabled: false,
    })
  })

  it('unwraps payload and computes route signatures', () => {
    expect(unwrapApiPayload({ data: { ok: true } })).toEqual({ ok: true })
    expect(unwrapApiPayload({ ok: true })).toEqual({ ok: true })
    expect(routeSignature({ mode: 'WALK', distanceM: 12, durationSec: 34, features: [], instructions: [] })).toBe('WALK:12:34:0:0')
  })

  it('extracts line coordinates and identifies turn actions', () => {
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

    expect(extractLineCoordinates(route)).toEqual([
      [113.2, 23.27],
      [113.21, 23.28],
    ])
    expect(isTurnAction('TURN_LEFT')).toBe(true)
    expect(isTurnAction('GO')).toBe(false)
  })

  it('builds a scaled instruction timeline', () => {
    const timeline = buildInstructionTimeline(
      [
        { action: 'GO', text: 'go straight', distanceM: 50 },
        { action: 'TURN_LEFT', text: 'left' },
        { action: 'GO', text: 'continue', distanceM: 50 },
        { action: 'ARRIVE', text: 'arrive' },
      ],
      120
    )

    expect(timeline).toHaveLength(4)
    expect(timeline[0]).toMatchObject({ action: 'GO', startM: 0, endM: 60, atM: 60 })
    expect(timeline[1]).toMatchObject({ action: 'TURN_LEFT', startM: 60, endM: 60, atM: 60 })
    expect(timeline[3]).toMatchObject({ action: 'ARRIVE', atM: 120 })
  })

  it('formats duration text and default nav info', () => {
    expect(formatDurationSec(90)).toBe('2 min')
    expect(formatDurationSec(3660)).toBe('1h 1m')
    expect(defaultNavInfo()).toMatchObject({
      currentText: '--',
      nextText: '--',
      remainingDistanceText: '--',
      remainingDurationText: '--',
    })
  })
})
