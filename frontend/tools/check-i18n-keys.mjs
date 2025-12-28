import enUS from '../src/locales/en-US.js'
import zhCN from '../src/locales/zh-CN.js'

function isPlainObject(value) {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function flattenKeys(obj, prefix = '') {
  if (!isPlainObject(obj)) {
    return prefix ? [prefix] : []
  }

  const keys = []
  for (const [key, value] of Object.entries(obj)) {
    const next = prefix ? `${prefix}.${key}` : key
    if (isPlainObject(value)) {
      keys.push(...flattenKeys(value, next))
    } else {
      keys.push(next)
    }
  }
  return keys
}

function toLeafMap(obj, prefix = '', out = new Map()) {
  if (!isPlainObject(obj)) {
    if (prefix) {
      out.set(prefix, obj)
    }
    return out
  }

  for (const [key, value] of Object.entries(obj)) {
    const next = prefix ? `${prefix}.${key}` : key
    if (isPlainObject(value)) {
      toLeafMap(value, next, out)
    } else {
      out.set(next, value)
    }
  }
  return out
}

function extractPlaceholders(value) {
  if (typeof value !== 'string') {
    return new Set()
  }
  const result = new Set()
  const regex = /\{([a-zA-Z0-9_]+)\}/g
  let match = null
  while ((match = regex.exec(value)) !== null) {
    result.add(match[1])
  }
  return result
}

function setToSortedArray(valueSet) {
  return Array.from(valueSet).sort((left, right) => left.localeCompare(right))
}

function diffSet(left, right) {
  const output = []
  for (const item of left) {
    if (!right.has(item)) {
      output.push(item)
    }
  }
  return output.sort((a, b) => a.localeCompare(b))
}

const enKeys = new Set(flattenKeys(enUS))
const zhKeys = new Set(flattenKeys(zhCN))
const missingInZh = diffSet(enKeys, zhKeys)
const extraInZh = diffSet(zhKeys, enKeys)

const enLeafMap = toLeafMap(enUS)
const zhLeafMap = toLeafMap(zhCN)
const placeholderMismatches = []

for (const key of enKeys) {
  if (!zhLeafMap.has(key)) {
    continue
  }

  const enPlaceholders = extractPlaceholders(enLeafMap.get(key))
  const zhPlaceholders = extractPlaceholders(zhLeafMap.get(key))
  const missing = diffSet(enPlaceholders, zhPlaceholders)
  const extra = diffSet(zhPlaceholders, enPlaceholders)
  if (missing.length || extra.length) {
    placeholderMismatches.push({ key, missing, extra })
  }
}

console.log('[i18n:check] key summary')
console.log(`  en-US keys: ${enKeys.size}`)
console.log(`  zh-CN keys: ${zhKeys.size}`)
console.log(`  missing in zh-CN: ${missingInZh.length}`)
console.log(`  extra in zh-CN: ${extraInZh.length}`)
console.log(`  placeholder mismatches: ${placeholderMismatches.length}`)

if (missingInZh.length) {
  console.log('\n[i18n:check] Missing keys in zh-CN:')
  for (const key of missingInZh) {
    console.log(`  - ${key}`)
  }
}

if (extraInZh.length) {
  console.log('\n[i18n:check] Extra keys in zh-CN:')
  for (const key of extraInZh) {
    console.log(`  - ${key}`)
  }
}

if (placeholderMismatches.length) {
  console.log('\n[i18n:check] Placeholder mismatches:')
  for (const item of placeholderMismatches) {
    const missing = item.missing.length ? setToSortedArray(new Set(item.missing)).join(', ') : '-'
    const extra = item.extra.length ? setToSortedArray(new Set(item.extra)).join(', ') : '-'
    console.log(`  - ${item.key} | missing: ${missing} | extra: ${extra}`)
  }
}

if (missingInZh.length || extraInZh.length || placeholderMismatches.length) {
  process.exitCode = 1
} else {
  console.log('\n[i18n:check] PASS')
}
