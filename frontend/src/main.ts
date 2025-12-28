import { createApp, ref } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import i18n from './locales'
import { useClientErrorReporting } from './composables/useClientErrorReporting'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)

const reportingToggle = String(import.meta.env.VITE_ENABLE_CLIENT_ERROR_REPORTING ?? '1')
const reportingEnabled = !['0', 'false', 'off', 'no'].includes(reportingToggle.trim().toLowerCase())
if (reportingEnabled && typeof window !== 'undefined') {
  const clientErrorState = ref({ lastSentAt: 0 })
  const apiBaseUrl = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  const clientErrorUrl = apiBaseUrl ? `${apiBaseUrl}/api/client/error` : '/api/client/error'
  const { installClientErrorHandlers } = useClientErrorReporting({ clientErrorState, clientErrorUrl })
  installClientErrorHandlers()
}

app.mount('#app')
