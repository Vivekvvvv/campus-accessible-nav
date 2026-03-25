
<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { postJson } from '../utils/fetchUtils'

const { t } = useI18n()

const props = defineProps({
  visible: { type: Boolean, default: false },
  lat: { type: Number, default: Number.NaN },
  lng: { type: Number, default: Number.NaN },
})

const emit = defineEmits(['close', 'success'])

const type = ref('blocked')
const reason = ref('')
const submitterName = ref('')
const loading = ref(false)
const error = ref('')
const photos = ref([])
const uploadingPhotos = ref(false)

const previewUrls = computed(() => photos.value.map(f => URL.createObjectURL(f)))

const typeOptions = computed(() => [
  { key: 'blocked', label: t('obstacle.typeBlocked') },
  { key: 'construction', label: t('obstacle.typeConstruction') },
  { key: 'stairs', label: t('obstacle.typeStairs') },
  { key: 'damage', label: t('obstacle.typeDamaged') },
  { key: 'other', label: t('obstacle.typeOther') },
])

const selectedCoordsText = computed(() => {
  const lat = Number(props.lat)
  const lng = Number(props.lng)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return '--'
  return t('obstacle.selectedLocation', { lng: lng.toFixed(6), lat: lat.toFixed(6) })
})

async function submit() {
  const lat = Number(props.lat)
  const lng = Number(props.lng)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return

  loading.value = true
  error.value = ''

  try {
    let photoUrls = []
    if (photos.value.length > 0) {
      uploadingPhotos.value = true
      const formData = new FormData()
      photos.value.forEach(f => formData.append('files', f))
      const uploadRes = await fetch('/api/files/upload-multiple', { method: 'POST', body: formData })
      if (!uploadRes.ok) throw new Error('Photo upload failed')
      const uploadData = await uploadRes.json()
      photoUrls = (uploadData.uploaded || uploadData || []).map(f => f.url || f)
      uploadingPhotos.value = false
    }

    const payload = {
      edgeId: null,
      submitterLat: lat,
      submitterLng: lng,
      type: type.value,
      reason: reason.value,
      submitterName: submitterName.value,
      photoUrls,
    }
    const res = await postJson('/api/obstacles/report', payload)
    emit('success', res)
    close()
  } catch (err) {
    error.value = err?.message || t('obstacle.submitFailed')
    uploadingPhotos.value = false
  } finally {
    loading.value = false
  }
}

function close() {
  emit('close')
  reason.value = ''
  error.value = ''
  type.value = 'blocked'
  photos.value = []
}

function handlePhotoSelect(event) {
  const input = event.target
  if (!input.files) return
  const remaining = 5 - photos.value.length
  const newFiles = Array.from(input.files).slice(0, remaining)
  photos.value.push(...newFiles)
  input.value = ''
}

function removePhoto(idx) {
  photos.value.splice(idx, 1)
}
</script>

<template>
  <!-- 障碍上报弹窗：全屏遮罩 + 居中卡片 -->
  <div v-if="visible" class="report-overlay" @click.self="close">
    <div class="report-modal">
      <!-- 弹窗标题 -->
      <div class="modal-header">
        <span class="modal-icon">⚠</span>
        <h3 class="modal-title">{{ t('obstacle.reportTitle') }}</h3>
        <button class="modal-close" @click="close" aria-label="close">✕</button>
      </div>

      <div class="modal-body">
        <!-- 坐标提示 -->
        <div class="coords-badge">
          <span class="coords-icon">⊙</span>
          {{ selectedCoordsText }}
        </div>

        <!-- 障碍类型 -->
        <div class="form-group">
          <label class="form-label">{{ t('obstacle.type') }}</label>
          <div class="type-chips">
            <button v-for="opt in typeOptions" :key="opt.key" type="button" class="type-chip" :class="{ active: type === opt.key }" @click="type = opt.key">
              {{ opt.label }}
            </button>
          </div>
        </div>

        <!-- 描述 -->
        <div class="form-group">
          <label class="form-label">{{ t('obstacle.description') }}</label>
          <textarea v-model="reason" class="form-textarea" rows="3" :placeholder="t('obstacle.descriptionPlaceholder')"></textarea>
        </div>

        <!-- 提交者（可选）-->
        <div class="form-group">
          <label class="form-label">{{ t('obstacle.submitterNameOptional') }}</label>
          <input v-model="submitterName" class="form-input" :placeholder="t('obstacle.anonymous')" />
        </div>

        <!-- 照片上传 -->
        <div class="form-group">
          <label class="form-label">{{ t('obstacle.photos') }}</label>
          <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple :disabled="loading || photos.length >= 5" class="file-input" @change="handlePhotoSelect" />
          <p class="form-hint">{{ t('obstacle.photosHint', { max: 5 }) }}</p>
          <div v-if="photos.length" class="photo-preview">
            <div v-for="(url, idx) in previewUrls" :key="idx" class="photo-thumb">
              <img :src="url" alt="preview" />
              <button type="button" class="photo-remove" @click="removePhoto(idx)">✕</button>
            </div>
          </div>
          <div v-if="uploadingPhotos" class="uploading-hint"><span class="spin"></span> {{ t('obstacle.uploading') }}</div>
        </div>

        <!-- 错误信息 -->
        <div v-if="error" class="error-bar">
          <span class="error-icon">!</span> {{ error }}
        </div>

        <!-- 操作按钮 -->
        <div class="modal-actions">
          <button class="cancel-btn" @click="close" :disabled="loading">{{ t('common.cancel') }}</button>
          <button class="submit-btn" @click="submit" :disabled="loading">
            <span v-if="loading" class="spin"></span>
            {{ loading ? t('obstacle.submitting') : t('common.submit') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.45);
  display: flex; align-items: center; justify-content: center;
  z-index: 2000; padding: 16px;
  backdrop-filter: blur(2px);
}
.report-modal {
  background: var(--ui-card, #fff);
  width: 100%; max-width: 420px;
  border-radius: 18px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
  overflow: hidden;
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
  animation: modal-in 0.2s ease;
}
@keyframes modal-in { from { transform: scale(0.95) translateY(8px); opacity: 0; } to { transform: scale(1) translateY(0); opacity: 1; } }
.modal-header {
  display: flex; align-items: center; gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
}
.modal-icon { font-size: 18px; color: #f97316; }
.modal-title { flex: 1; margin: 0; font-size: 15px; font-weight: 700; color: var(--ui-ink); }
.modal-close {
  width: 28px; height: 28px; border-radius: 50%;
  border: none; background: var(--ui-line, #e5e7eb);
  color: var(--ui-muted, #6b7280); font-size: 12px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.15s;
}
.modal-close:hover { background: rgba(239,68,68,0.15); color: #dc2626; }
.modal-body { padding: 18px 20px; display: flex; flex-direction: column; gap: 14px; }
.coords-badge {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px; border-radius: 8px;
  background: rgba(14,165,164,0.06); border: 1px solid rgba(14,165,164,0.15);
  font-size: 12px; color: var(--ui-muted, #6b7280); font-family: monospace;
}
.coords-icon { color: var(--ui-accent, #0ea5a4); font-size: 14px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 12px; font-weight: 700; color: var(--ui-muted, #9ca3af); text-transform: uppercase; letter-spacing: 0.05em; }
.type-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.type-chip {
  padding: 5px 12px; border-radius: 999px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb);
  color: var(--ui-muted, #6b7280);
  font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s;
}
.type-chip.active { border-color: #f97316; background: rgba(249,115,22,0.08); color: #f97316; }
.type-chip:hover:not(.active) { border-color: #f97316; color: var(--ui-ink); }
.form-input {
  padding: 9px 12px; border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px; background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink); font-size: 13px; outline: none; transition: border-color 0.15s;
}
.form-input:focus { border-color: var(--ui-accent, #0ea5a4); }
.form-textarea {
  padding: 9px 12px; border: 1.5px solid var(--ui-line, #e5e7eb);
  border-radius: 9px; background: var(--ui-bg, #f9fafb);
  color: var(--ui-ink); font-size: 13px; outline: none; resize: vertical; transition: border-color 0.15s; font-family: inherit;
}
.form-textarea:focus { border-color: var(--ui-accent, #0ea5a4); }
.form-hint { font-size: 11px; color: var(--ui-muted, #9ca3af); margin: 0; }
.file-input { font-size: 13px; color: var(--ui-ink); }
.photo-preview { display: flex; gap: 8px; flex-wrap: wrap; }
.photo-thumb {
  position: relative; width: 64px; height: 64px;
  border-radius: 8px; overflow: hidden; border: 1px solid var(--ui-line, #e5e7eb);
}
.photo-thumb img { width: 100%; height: 100%; object-fit: cover; }
.photo-remove {
  position: absolute; top: 2px; right: 2px;
  width: 18px; height: 18px; border-radius: 50%;
  border: none; background: rgba(0,0,0,0.5); color: #fff;
  font-size: 10px; cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.uploading-hint { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--ui-muted, #9ca3af); }
.error-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border-radius: 8px;
  background: rgba(239,68,68,0.06); border: 1px solid rgba(239,68,68,0.2);
  color: #dc2626; font-size: 12px;
}
.error-icon {
  width: 16px; height: 16px; border-radius: 50%;
  background: rgba(239,68,68,0.15);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 800; flex-shrink: 0;
}
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; }
.cancel-btn {
  padding: 8px 18px; border-radius: 9px;
  border: 1.5px solid var(--ui-line, #e5e7eb);
  background: var(--ui-bg, #f9fafb); color: var(--ui-muted, #6b7280);
  font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.15s;
}
.cancel-btn:hover { border-color: var(--ui-muted); color: var(--ui-ink); }
.cancel-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.submit-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 22px; border-radius: 9px;
  border: none; background: var(--ui-accent, #0ea5a4); color: #fff;
  font-size: 13px; font-weight: 700; cursor: pointer; transition: all 0.15s;
}
.submit-btn:hover:not(:disabled) { filter: brightness(1.05); }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.spin {
  width: 12px; height: 12px;
  border: 2px solid rgba(255,255,255,0.4); border-top-color: #fff;
  border-radius: 50%; animation: spin 0.7s linear infinite; flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
