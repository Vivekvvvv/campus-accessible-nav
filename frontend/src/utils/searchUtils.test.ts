import { describe, expect, it } from 'vitest'

import {
  applyCategories,
  attachPinyinToItem,
  buildAliasList,
  deriveCategories,
  escapeHtml,
  escapeRegExp,
  fuzzyMatch,
  highlightMatch,
  isLatinQuery,
  isSubsequence,
  normalizeSearchText,
  normalizeText,
} from './searchUtils'

describe('searchUtils', () => {
  it('normalizes text and query consistently', () => {
    expect(normalizeText('  Hello World  ')).toBe('hello world')
    expect(normalizeSearchText(' A B  C ')).toBe('abc')
  })

  it('detects latin query and subsequence matches', () => {
    expect(isLatinQuery('abc')).toBe(true)
    expect(isLatinQuery('图书馆')).toBe(false)
    expect(isSubsequence('library', 'lby')).toBe(true)
    expect(isSubsequence('library', 'lzz')).toBe(false)
  })

  it('supports fuzzy matching on strings and searchable items', () => {
    expect(fuzzyMatch('library', 'lib')).toBe(true)
    expect(fuzzyMatch('library', 'lby')).toBe(true)
    expect(
      fuzzyMatch(
        {
          name: '图书馆',
          pinyin: 'tushuguan',
          initials: 'tsg',
        },
        'tsg'
      )
    ).toBe(true)
  })

  it('derives and applies categories from text context', () => {
    expect(deriveCategories({ name: '第一教学楼', group: '', tags: [] })).toContain('teaching')
    expect(deriveCategories({ name: '学生宿舍', group: '', tags: [] })).toContain('dorm')
    expect(applyCategories({ name: '服务中心', group: '', tags: [] })).toMatchObject({
      categories: ['service'],
    })
  })

  it('attaches pinyin and initials when converter exists', () => {
    const result = attachPinyinToItem(
      { name: '图书馆' },
      (_text: string, opts?: Record<string, unknown>) =>
        opts?.pattern === 'first' ? 'TSG' : 'Tu Shu Guan'
    )

    expect(result).toMatchObject({
      pinyin: 'tushuguan',
      initials: 'tsg',
    })
  })

  it('builds aliases, escapes html/regexp and highlights matches', () => {
    const aliases = buildAliasList(
      {
        alias: '图书馆',
        alt_name: 'Library',
        brand: 'Campus',
        name: 'Library',
      },
      'Library'
    )
    expect(aliases).toEqual(['图书馆', 'Campus'])
    expect(escapeHtml('<div>"a"&\'b\'</div>')).toContain('&lt;div&gt;')
    expect(escapeRegExp('a+b?')).toBe('a\\+b\\?')
    expect(highlightMatch('Hello Library', 'lib')).toContain('search-highlight')
  })
})
