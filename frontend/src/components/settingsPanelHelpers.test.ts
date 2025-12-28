import { describe, expect, it } from 'vitest'

import {
  buildDefaultAccessibilityProfile,
  buildDefaultVoicePolicy,
  normalizeAccessibilityProfile,
  normalizeVoicePolicy,
} from './settingsPanelHelpers'

describe('settingsPanelHelpers', () => {
  it('builds default accessibility profile', () => {
    expect(buildDefaultAccessibilityProfile()).toEqual({
      mobilityMode: 'WALK',
      avoidStairs: false,
      avoidSlope: false,
      avoidConstruction: true,
      maxSlopePercent: 12,
    })
  })

  it('normalizes accessibility profile values', () => {
    expect(
      normalizeAccessibilityProfile({
        mobilityMode: 'wheelchair',
        avoidStairs: 1,
        avoidSlope: '',
        avoidConstruction: 0,
        maxSlopePercent: 99,
      }),
    ).toEqual({
      mobilityMode: 'WHEELCHAIR',
      avoidStairs: true,
      avoidSlope: false,
      avoidConstruction: false,
      maxSlopePercent: 45,
    })
  })

  it('builds and normalizes default voice policy', () => {
    expect(buildDefaultVoicePolicy()).toEqual({
      preTurnM: 12,
      preArrivalM: 20,
      announceIntervalM: 20,
      quietHoursStart: '',
      quietHoursEnd: '',
      vibrateEnabled: true,
    })

    expect(
      normalizeVoicePolicy({
        preTurnM: 999,
        preArrivalM: -1,
        announceIntervalM: 30,
        quietHoursStart: ' 08:00 ',
        quietHoursEnd: '09:00',
        vibrateEnabled: 0,
      }),
    ).toEqual({
      preTurnM: 500,
      preArrivalM: 0,
      announceIntervalM: 30,
      quietHoursStart: '08:00',
      quietHoursEnd: '09:00',
      vibrateEnabled: false,
    })
  })
})
