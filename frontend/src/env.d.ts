/// <reference types="vite/client" />

type ViteBooleanLike = '0' | '1' | 'false' | 'true' | 'no' | 'yes' | 'off' | 'on'

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_ENABLE_CLIENT_ERROR_REPORTING?: ViteBooleanLike
  readonly VITE_DISABLE_MAP?: ViteBooleanLike
  readonly VITE_MAPTILER_KEY?: string

  readonly VITE_AUTH_TOKEN_SKEW_SECONDS?: `${number}`
  readonly VITE_AUTH_GUARD_INTERVAL_MS?: `${number}`
  readonly VITE_AUTH_EXPIRY_WARNING_MINUTES?: `${number}`
  readonly VITE_AUTH_EXPIRY_WARNING_SNOOZE_MINUTES?: `${number}`
  readonly VITE_AUTH_EXPIRY_CRITICAL_PULSE_ENABLED?: ViteBooleanLike
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

// Web Speech API types for SpeechRecognition (not yet fully standard in TS lib)
interface SpeechRecognitionEvent extends Event {
  results: SpeechRecognitionResultList
  resultIndex: number
}

interface SpeechRecognitionErrorEvent extends Event {
  error: string
  message: string
}

/* eslint-disable no-redeclare */
interface SpeechRecognition extends EventTarget {
  lang: string
  continuous: boolean
  interimResults: boolean
  onstart: ((this: SpeechRecognition, ev: Event) => void) | null
  onend: ((this: SpeechRecognition, ev: Event) => void) | null
  onresult: ((this: SpeechRecognition, ev: SpeechRecognitionEvent) => void) | null
  onerror: ((this: SpeechRecognition, ev: SpeechRecognitionErrorEvent) => void) | null
  start(): void
  stop(): void
  abort(): void
}

declare var SpeechRecognition: {
  new(): SpeechRecognition
  prototype: SpeechRecognition
}
/* eslint-enable no-redeclare */
