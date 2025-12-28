
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
  <div v-if="visible" class="report-modal-overlay">
    <div class="report-modal">
      <div class="modal-header">
        <h3>{{ t('obstacle.reportTitle') }}</h3>
        <button class="close-btn" @click="close" aria-label="close">×</button>
      </div>

      <div class="modal-body">
        <div class="form-item">
          <label>{{ t('obstacle.locationLabel') }}</label>
          <div class="hint">{{ selectedCoordsText }}</div>
        </div>

        <div class="form-item">
          <label>{{ t('obstacle.type') }}</label>
          <select v-model="type" class="input">
            <option v-for="opt in typeOptions" :key="opt.key" :value="opt.key">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="form-item">
          <label>{{ t('obstacle.description') }}</label>
          <textarea
            v-model="reason"
            class="textarea"
            rows="3"
            :placeholder="t('obstacle.descriptionPlaceholder')"
          ></textarea>
        </div>

        <div class="form-item">
          <label>{{ t('obstacle.submitterNameOptional') }}</label>
          <input v-model="submitterName" class="input" :placeholder="t('obstacle.anonymous')" />
        </div>

        <div class="form-item">
          <label>{{ t('obstacle.photos') }}</label>
          <input
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            multiple
            :disabled="loading || photos.length >= 5"
            class="input"
            @change="handlePhotoSelect"
          />
          <div class="hint">{{ t('obstacle.photosHint', { max: 5 }) }}</div>
          <div v-if="photos.length" class="photo-preview">
            <div v-for="(url, idx) in previewUrls" :key="idx" class="photo-thumb">
              <img :src="url" alt="preview" />
              <button type="button" class="photo-remove" @click="removePhoto(idx)">&times;</button>
            </div>
          </div>
          <div v-if="uploadingPhotos" class="hint">{{ t('obstacle.uploading') }}</div>
        </div>

        <div v-if="error" class="error-msg">{{ error }}</div>

        <div class="actions">
          <button class="btn" @click="close" :disabled="loading">{{ t('common.cancel') }}</button>
          <button class="btn primary" @click="submit" :disabled="loading">
            {{ loading ? t('obstacle.submitting') : t('common.submit') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.report-modal {
  background: white;
  width: 90%;
  max-width: 400px;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.modal-header {
  padding: 12px 16px;
  background: #f3f4f6;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #6b7280;
}

.modal-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-item label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.hint {
  font-size: 12px;
  color: #6b7280;
}

.input,
.textarea {
  padding: 8px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.btn {
  padding: 6px 16px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: white;
  cursor: pointer;
  font-size: 14px;
}

.btn.primary {
  background: #2563eb;
  color: white;
  border-color: #2563eb;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error-msg {
  color: #dc2626;
  font-size: 12px;
}

.photo-preview {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.photo-thumb {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #d1d5db;
}

.photo-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-remove {
  position: absolute;
  top: 0;
  right: 0;
  background: rgba(0,0,0,0.5);
  color: white;
  border: none;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 0 0 0 4px;
}
</style>
