import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { logger } from '../utils/logger'

export type ThemeMode = 'light' | 'dark' | 'auto'

/**
 * 主题管理 Store
 * 支持浅色、深色和跟随系统三种模式
 */
export const useThemeStore = defineStore('theme', () => {
  // 主题模式: 'light' | 'dark' | 'auto'
  const mode = ref<ThemeMode>(getStoredTheme())

  // 系统偏好
  const systemPrefersDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)

  // 计算实际应用的主题
  const resolvedTheme = computed<'light' | 'dark'>(() => {
    if (mode.value === 'auto') {
      return systemPrefersDark.value ? 'dark' : 'light'
    }
    return mode.value
  })

  // 是否是深色模式
  const isDark = computed(() => resolvedTheme.value === 'dark')

  // 监听系统主题变化
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQuery.addEventListener('change', (e) => {
    systemPrefersDark.value = e.matches
  })

  // 设置主题
  function setTheme(newMode: ThemeMode): void {
    if (!['light', 'dark', 'auto'].includes(newMode)) {
      logger.warn(`[Theme] Invalid theme mode: ${newMode}`)
      return
    }
    mode.value = newMode
    localStorage.setItem('accessiblenav_theme', newMode)
    applyTheme()
  }

  // 切换深色/浅色模式
  function toggleDark(): void {
    if (mode.value === 'auto') {
      setTheme(systemPrefersDark.value ? 'light' : 'dark')
    } else {
      setTheme(mode.value === 'dark' ? 'light' : 'dark')
    }
  }

  // 应用主题到 DOM
  function applyTheme(): void {
    const theme = resolvedTheme.value
    document.documentElement.setAttribute('data-theme', theme)
    document.documentElement.classList.toggle('dark', theme === 'dark')

    // 更新 meta theme-color
    const metaThemeColor = document.querySelector('meta[name="theme-color"]')
    if (metaThemeColor) {
      metaThemeColor.setAttribute('content', theme === 'dark' ? '#0f172a' : '#ffffff')
    }

    logger.debug(`[Theme] Applied theme: ${theme}`)
  }

  // 获取存储的主题
  function getStoredTheme(): ThemeMode {
    const stored = localStorage.getItem('accessiblenav_theme')
    if (stored && ['light', 'dark', 'auto'].includes(stored)) {
      return stored as ThemeMode
    }
    return 'auto' // 默认跟随系统
  }

  // 监听 resolvedTheme 变化
  watch(resolvedTheme, () => {
    applyTheme()
  }, { immediate: true })

  return {
    mode,
    resolvedTheme,
    isDark,
    setTheme,
    toggleDark
  }
})
