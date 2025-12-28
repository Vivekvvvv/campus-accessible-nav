import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/utils/logger', () => ({
  logger: {
    debug: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    log: vi.fn(),
  },
}))

describe('useThemeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.classList.remove('dark')
    document.head.innerHTML = '<meta name="theme-color" content="#ffffff">'
  })

  it('uses stored theme and applies DOM attributes', async () => {
    localStorage.setItem('accessiblenav_theme', 'dark')
    const { useThemeStore } = await import('@/stores/useThemeStore')
    const store = useThemeStore()

    expect(store.mode).toBe('dark')
    expect(store.resolvedTheme).toBe('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#0f172a')
  })

  it('toggles dark mode from explicit and auto modes', async () => {
    const { useThemeStore } = await import('@/stores/useThemeStore')
    const store = useThemeStore()

    store.setTheme('light')
    store.toggleDark()
    expect(store.mode).toBe('dark')

    store.setTheme('auto')
    store.toggleDark()
    expect(['light', 'dark']).toContain(store.mode)
  })

  it('ignores invalid theme values', async () => {
    const { useThemeStore } = await import('@/stores/useThemeStore')
    const store = useThemeStore()

    store.setTheme('light')
    store.setTheme('invalid' as never)

    expect(store.mode).toBe('light')
  })
})
