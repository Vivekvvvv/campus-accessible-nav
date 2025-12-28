import { describe, expect, it } from 'vitest'

import {
  buildLevelHintText,
  formatCoordsText,
  formatHazardText,
  formatOffRouteText,
} from './navigationPanelHelpers'

describe('navigationPanelHelpers', () => {
  it('formats coordinates and off-route text', () => {
    expect(formatCoordsText({ lat: 23.2751234, lng: 113.2009876 })).toBe('23.275123, 113.200988')
    expect(formatCoordsText(null)).toBe('--')
    expect(formatOffRouteText(12.8)).toBe('13m')
    expect(formatOffRouteText('bad')).toBe('--')
  })

  it('formats hazard text', () => {
    expect(formatHazardText({ reason: 'construction', remainingM: 8.2 })).toBe('8m | construction')
    expect(formatHazardText(null)).toBe('')
  })

  it('builds level hint text only when transition is real', () => {
    const t = (key: string, params?: Record<string, unknown>) => {
      if (!params) return key
      return `${key}:${params.from}-${params.to}-${params.via}`
    }

    expect(
      buildLevelHintText(
        {
          currentLevel: 1,
          nextLevel: 3,
          levelTransitionVia: 'elevator',
        },
        t,
      ),
    ).toBe('navigation.levelTransitionHint:1-3-elevator')

    expect(
      buildLevelHintText(
        {
          currentLevel: 1,
          nextLevel: 1,
          levelTransitionVia: '',
        },
        t,
      ),
    ).toBe('')
  })
})
