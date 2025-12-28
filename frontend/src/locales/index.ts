import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'
import { logger } from '../utils/logger'

export const SUPPORTED_LOCALES = [
  // Keep flags ASCII to avoid console/encoding issues on some Windows setups.
  { code: 'zh-CN', name: '简体中文', flag: 'CN' },
  { code: 'en-US', name: 'English', flag: 'US' },
] as const

export type SupportedLocaleCode = typeof SUPPORTED_LOCALES[number]['code']

function getBrowserLocale(): string {
  const navigatorLocale = navigator.language
  if (!navigatorLocale) return 'zh-CN'

  const locale = SUPPORTED_LOCALES.find(
    (l) => l.code === navigatorLocale || l.code.split('-')[0] === navigatorLocale.split('-')[0]
  )
  return locale ? locale.code : 'zh-CN'
}

function getStoredLocale(): string {
  const stored = localStorage.getItem('accessiblenav_locale')
  if (stored && SUPPORTED_LOCALES.find((l) => l.code === stored)) {
    return stored
  }
  return getBrowserLocale()
}

const i18n = createI18n({
  legacy: false,
  locale: getStoredLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
  missingWarn: import.meta.env.DEV,
  fallbackWarn: import.meta.env.DEV,
})

export function setLocale(locale: string): boolean {
  if (!SUPPORTED_LOCALES.find((l) => l.code === locale)) {
    logger.warn(`[i18n] Unsupported locale: ${locale}`)
    return false
  }

  i18n.global.locale.value = locale as SupportedLocaleCode
  localStorage.setItem('accessiblenav_locale', locale)
  document.documentElement.setAttribute('lang', locale)
  logger.debug(`[i18n] Locale changed to: ${locale}`)
  return true
}

export function getLocale() {
  return i18n.global.locale.value
}

export function isZhCN() {
  return i18n.global.locale.value === 'zh-CN'
}

document.documentElement.setAttribute('lang', getStoredLocale())

export default i18n
