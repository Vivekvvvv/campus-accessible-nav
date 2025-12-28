import { describe, expect, it } from 'vitest'

import {
  extractLineCoordinates,
  extractLineEndCoordinate,
  formatDistanceM,
  nowIso,
  pickActiveMode,
  toSessionDestination,
} from './navigationSessionHelpers'

describe('navigationSessionHelpers', () => {
  it('returns ISO timestamps', () => {
    expect(() => new Date(nowIso())).not.toThrow()
  })

  it('maps active mode to backend mode', () => {
    expect(pickActiveMode('wheel')).toBe('WHEELCHAIR')
    expect(pickActiveMode('walk')).toBe('WALK')
  })

  it('extracts coordinates and end points from route geometry', () => {
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
    expect(extractLineEndCoordinate(route)).toEqual({ lng: 113.21, lat: 23.28 })
  })

  it('formats distance text', () => {
    expect(formatDistanceM(120)).toBe('120 m')
    expect(formatDistanceM(1234)).toBe('1.23 km')
    expect(formatDistanceM(Number.NaN)).toBe('--')
  })

  it('keeps current destination when payload is incomplete', () => {
    const current = { lng: 113.2, lat: 23.27, name: 'current' }
    expect(toSessionDestination({}, current)).toEqual(current)
  })

  it('builds destination from backend payload aliases', () => {
    expect(
      toSessionDestination(
        {
          destination_lng: 113.25,
          destination_lat: 23.29,
          destination_name: 'Library',
        },
        null
      )
    ).toEqual({
      lng: 113.25,
      lat: 23.29,
      name: 'Library',
    })
  })
})
