import { describe, expect, it } from 'vitest'

import { distanceToLineStringMeters, projectToLineStringMeters } from './polylineUtils'

describe('polylineUtils', () => {
  it('returns Infinity or null for invalid inputs', () => {
    expect(distanceToLineStringMeters({ lng: NaN, lat: 23.27 }, [])).toBe(Infinity)
    expect(projectToLineStringMeters({ lng: 113.2, lat: 23.27 }, [])).toBeNull()
  })

  it('computes distance from a point to a line string', () => {
    const distance = distanceToLineStringMeters(
      { lng: 113.2005, lat: 23.2705 },
      [
        [113.2, 23.27],
        [113.201, 23.27],
      ]
    )

    expect(distance).toBeGreaterThan(0)
    expect(distance).toBeLessThan(100)
  })

  it('projects a point onto a line and reports along-route meters', () => {
    const projection = projectToLineStringMeters(
      { lng: 113.2005, lat: 23.27 },
      [
        [113.2, 23.27],
        [113.201, 23.27],
      ]
    )

    expect(projection).not.toBeNull()
    expect(projection?.distanceToLineM).toBeLessThan(1)
    expect(projection?.alongM).toBeGreaterThan(0)
    expect(projection?.totalM).toBeGreaterThan(projection?.alongM || 0)
  })

  it('handles degenerate segments safely', () => {
    const projection = projectToLineStringMeters(
      { lng: 113.2, lat: 23.27 },
      [
        [113.2, 23.27],
        [113.2, 23.27],
        [113.201, 23.271],
      ]
    )

    expect(projection).not.toBeNull()
    expect(projection?.totalM).toBeGreaterThan(0)
  })
})
