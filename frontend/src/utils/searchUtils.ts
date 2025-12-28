export interface SearchableItem {
  name?: string
  group?: string
  tags?: string[]
  pinyin?: string
  initials?: string
  categories?: string[]
  [key: string]: unknown
}

export function normalizeText(v: unknown): string {
  return String(v || '').trim().toLowerCase()
}

export function normalizeSearchText(v: unknown): string {
  return normalizeText(v).replace(/\s+/g, '')
}

export function isLatinQuery(q: string): boolean {
  return /[a-zA-Z]/.test(q || '')
}

export function isSubsequence(hay: string, needle: string): boolean {
  let i = 0
  let j = 0
  while (i < hay.length && j < needle.length) {
    if (hay[i] === needle[j]) j++
    i++
  }
  return j === needle.length
}

export function fuzzyMatch(hay: SearchableItem | string, q: string): boolean {
  if (!hay || !q) return false
  if (typeof hay === 'string') {
    if (hay.includes(q)) return true
    if (q.length <= 1) return false
    return isSubsequence(hay, q)
  }
  const name = normalizeSearchText(hay.name)
  const pinyin = hay.pinyin || ''
  const initials = hay.initials || ''
  if (name.includes(q)) return true
  if (pinyin && pinyin.includes(q)) return true
  if (initials && initials.includes(q)) return true
  if (q.length <= 1) return false
  return isSubsequence(name, q) || (pinyin ? isSubsequence(pinyin, q) : false)
}

export function deriveCategories({ name, group, tags }: { name?: string; group?: string; tags?: string[] }): string[] {
  const out = new Set<string>()
  const text = normalizeText(`${name || ''} ${group || ''} ${(tags || []).join(' ')}`)

  const addIf = (key: string, patterns: string[]) => {
    for (const p of patterns) {
      if (text.includes(p)) {
        out.add(key)
        return
      }
    }
  }

  addIf('teaching', ['教学楼', '教学', '学院', '实验', '图书馆'])
  addIf('dorm', ['宿舍', '公寓', '寝室'])
  addIf('canteen', ['食堂', '餐厅', '饭堂', '餐饮', '咖啡'])
  addIf('service', ['服务', '医务', '医院', '保卫', '后勤', '超市', '便利', '快递', '行政', '中心'])

  return Array.from(out)
}

export function applyCategories(item: SearchableItem): SearchableItem {
  const categories = deriveCategories(item as { name?: string; group?: string; tags?: string[] })
  return { ...item, categories }
}

export function attachPinyinToItem(item: SearchableItem, pinyinFn: ((text: string, opts?: Record<string, unknown>) => string) | null): SearchableItem {
  if (!item?.name || !pinyinFn) return item
  try {
    const full = pinyinFn(item.name, { toneType: 'none' })
    const initials = pinyinFn(item.name, { toneType: 'none', pattern: 'first' })
    return {
      ...item,
      pinyin: normalizeSearchText(full || ''),
      initials: normalizeSearchText(initials || ''),
    }
  } catch {
    return item
  }
}

export function buildAliasList(props: Record<string, unknown> | null, fallbackName?: string): string[] {
  const aliases: string[] = []
  if (!props) return aliases
  const seen = new Set<string>()
  const add = (value: unknown) => {
    if (value === null || value === undefined) return
    const text = String(value).trim()
    if (!text) return
    if (seen.has(text)) return
    if (fallbackName && text === fallbackName) return
    seen.add(text)
    aliases.push(text)
  }
  const fields = [
    'alias',
    'alt_name',
    'alt_name:zh',
    'alt_name:en',
    'name:en',
    'short_name',
    'official_name',
    'brand',
    'operator',
  ]
  for (const field of fields) {
    add(props[field])
  }
  return aliases
}

export function escapeHtml(value: unknown): string {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function escapeRegExp(value: unknown): string {
  return String(value || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function highlightMatch(text: string, query: string): string {
  if (!text) return ''
  const raw = escapeHtml(text)
  const term = String(query || '').trim()
  if (!term) return raw
  const regex = new RegExp(`(${escapeRegExp(term)})`, 'ig')
  return raw.replace(regex, '<span class="search-highlight">$1</span>')
}
