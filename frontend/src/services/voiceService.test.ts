import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/logger', () => ({
  logger: {
    debug: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  },
}))

class MockSpeechSynthesisUtterance {
  text: string
  voice: SpeechSynthesisVoice | null = null
  rate = 1
  pitch = 1
  volume = 1
  lang = ''
  onstart: (() => void) | null = null
  onend: (() => void) | null = null
  onerror: ((event: { error: string }) => void) | null = null

  constructor(text: string) {
    this.text = text
  }
}

describe('voiceService', () => {
  beforeEach(() => {
    vi.resetModules()
    localStorage.clear()

    const speak = vi.fn((utterance: MockSpeechSynthesisUtterance) => {
      utterance.onstart?.()
      utterance.onend?.()
    })

    globalThis.SpeechSynthesisUtterance = MockSpeechSynthesisUtterance as unknown as typeof SpeechSynthesisUtterance
    globalThis.speechSynthesis = {
      speak,
      cancel: vi.fn(),
      pause: vi.fn(),
      resume: vi.fn(),
      getVoices: vi.fn().mockReturnValue([
        { name: 'zh-local', lang: 'zh-CN', localService: true },
        { name: 'en-remote', lang: 'en-US', localService: false },
      ]),
      onvoiceschanged: null,
    } as unknown as SpeechSynthesis
    Object.defineProperty(window, 'speechSynthesis', {
      configurable: true,
      value: globalThis.speechSynthesis,
    })
  })

  it('loads persisted settings and saves updates', async () => {
    localStorage.setItem('voiceSettings', JSON.stringify({ rate: 1.5, enabled: false }))
    const { default: voiceService } = await import('./voiceService')

    expect(voiceService.getSettings()).toMatchObject({ rate: 1.5, enabled: false })

    voiceService.updateSettings({ enabled: true, volume: 0.5 })

    expect(voiceService.getSettings()).toMatchObject({ enabled: true, volume: 0.5 })
    expect(localStorage.getItem('voiceSettings')).toContain('"volume":0.5')
  })

  it('speaks immediately with selected voice and current settings', async () => {
    const { default: voiceService } = await import('./voiceService')
    voiceService.updateSettings({ preferredVoice: 'zh-local', rate: 1.2, pitch: 0.9, volume: 0.6, enabled: true })

    voiceService.speakImmediate('导航开始')

    const utterance = vi.mocked(globalThis.speechSynthesis.speak).mock.calls[0][0] as unknown as MockSpeechSynthesisUtterance
    expect(utterance.text).toBe('导航开始')
    expect(utterance.voice?.name).toBe('zh-local')
    expect(utterance.rate).toBe(1.2)
    expect(utterance.pitch).toBe(0.9)
    expect(utterance.volume).toBe(0.6)
    expect(utterance.lang).toBe('zh-CN')
  })

  it('does not enqueue or speak when disabled', async () => {
    const { default: voiceService } = await import('./voiceService')
    voiceService.updateSettings({ enabled: false })

    voiceService.speak('不会播放')
    voiceService.speakImmediate('也不会播放')

    expect(globalThis.speechSynthesis.speak).not.toHaveBeenCalled()
  })

  it('supports queue playback and semantic navigation helpers', async () => {
    const { default: voiceService } = await import('./voiceService')
    voiceService.updateSettings({ enabled: true })

    voiceService.speak('普通播报')
    voiceService.speakNavigationInstruction('前方左转')
    voiceService.speakArrival('图书馆')

    const spokenTexts = vi.mocked(globalThis.speechSynthesis.speak).mock.calls.map(
      ([utterance]) => (utterance as unknown as MockSpeechSynthesisUtterance).text
    )

    expect(spokenTexts).toContain('普通播报')
    expect(spokenTexts).toContain('前方左转')
    expect(spokenTexts).toContain('您已到达目的地：图书馆')
  })

  it('delegates pause, resume and stop to speechSynthesis', async () => {
    const { default: voiceService } = await import('./voiceService')

    voiceService.pause()
    voiceService.resume()
    voiceService.stop()

    expect(globalThis.speechSynthesis.pause).toHaveBeenCalled()
    expect(globalThis.speechSynthesis.resume).toHaveBeenCalled()
    expect(globalThis.speechSynthesis.cancel).toHaveBeenCalled()
  })
})
