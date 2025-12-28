export interface NavInfo {
  currentText: string
  nextText: string
  remainingDistanceM: number | null
  remainingDurationSec: number | null
  remainingDistanceText: string
  remainingDurationText: string
  remainingToNextM: number | null
  remainingToNextText: string
  _meta: NavInfoMeta | null
}

export interface NavInfoMeta {
  totalLineM: number
  alongM: number
  nearTurn: boolean
  nextIdx: number | null
  remainingToNextM: number | null
}

export interface VoicePolicy {
  preTurnM: number
  preArrivalM: number
  announceIntervalM: number
  quietHoursStart: string
  quietHoursEnd: string
  vibrateEnabled: boolean
}

export interface TimelineStep {
  idx: number
  action: string
  text: string
  startM: number
  endM: number
  atM: number
}
