import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { logger } from '../utils/logger'
import i18n from '../locales'

export interface HistoryPoint {
  lng: number
  lat: number
  name: string
}

export interface HistoryItem {
  id: string
  start: HistoryPoint
  end: HistoryPoint
  mode: string
  distanceM: number
  durationSec: number
  timestamp: number
}

/**
 * 导航历史记录 Store
 * 记录用户的导航历史，支持快速重走之前的路线
 */
export const useHistoryStore = defineStore('history', () => {
  const STORAGE_KEY = 'accessiblenav_nav_history'
  const MAX_HISTORY_ITEMS = 20

  // 历史记录列表
  const historyList = ref<HistoryItem[]>([])

  // 搜索/过滤关键词
  const filterKeyword = ref('')

  // 过滤后的历史记录
  const filteredHistory = computed(() => {
    const keyword = filterKeyword.value.trim().toLowerCase()
    if (!keyword) return historyList.value

    return historyList.value.filter(item => {
      const startName = (item.start?.name || '').toLowerCase()
      const endName = (item.end?.name || '').toLowerCase()
      return startName.includes(keyword) || endName.includes(keyword)
    })
  })

  // 从 localStorage 加载历史
  function loadHistory(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        if (Array.isArray(parsed)) {
          historyList.value = parsed.slice(0, MAX_HISTORY_ITEMS)
        }
      }
    } catch (e) {
      logger.warn('[HistoryStore] 加载历史记录失败:', e)
      historyList.value = []
    }
  }

  // 保存历史到 localStorage
  function saveHistory(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(historyList.value))
    } catch (e) {
      logger.warn('[HistoryStore] 保存历史记录失败:', e)
    }
  }

  /**
   * 添加一条导航历史
   */
  function addHistory({ start, end, mode, distanceM, durationSec }: {
    start: HistoryPoint
    end: HistoryPoint
    mode?: string
    distanceM?: number
    durationSec?: number
  }): void {
    if (!start || !end) return

    const unnamed = tr('map.unnamedPlace')
    const historyItem: HistoryItem = {
      id: Date.now().toString(36) + Math.random().toString(36).substring(2, 6),
      start: {
        lng: start.lng,
        lat: start.lat,
        name: start.name || unnamed
      },
      end: {
        lng: end.lng,
        lat: end.lat,
        name: end.name || unnamed
      },
      mode: mode || 'walk',
      distanceM: distanceM || 0,
      durationSec: durationSec || 0,
      timestamp: Date.now()
    }

    // 检查是否有重复（相同起终点）
    const existingIndex = historyList.value.findIndex(item =>
      Math.abs(item.start.lng - start.lng) < 0.0001 &&
      Math.abs(item.start.lat - start.lat) < 0.0001 &&
      Math.abs(item.end.lng - end.lng) < 0.0001 &&
      Math.abs(item.end.lat - end.lat) < 0.0001
    )

    if (existingIndex !== -1) {
      // 移除旧记录
      historyList.value.splice(existingIndex, 1)
    }

    // 添加到开头
    historyList.value.unshift(historyItem)

    // 限制数量
    if (historyList.value.length > MAX_HISTORY_ITEMS) {
      historyList.value = historyList.value.slice(0, MAX_HISTORY_ITEMS)
    }

    saveHistory()
    logger.debug('[HistoryStore] 添加历史记录:', historyItem)
  }

  /**
   * 删除一条历史记录
   */
  function removeHistory(id: string): void {
    const index = historyList.value.findIndex(item => item.id === id)
    if (index !== -1) {
      historyList.value.splice(index, 1)
      saveHistory()
    }
  }

  /**
   * 清空所有历史记录
   */
  function clearHistory(): void {
    historyList.value = []
    saveHistory()
  }

  /**
   * 设置过滤关键词
   */
  function setFilterKeyword(keyword: string): void {
    filterKeyword.value = keyword
  }

  /**
   * 格式化时间显示
   */
  function formatTime(timestamp: number): string {
    const date = new Date(timestamp)
    const now = new Date()
    const diff = now.getTime() - timestamp

    const locale = String(i18n.global.locale?.value || 'zh-CN')

    // 不到1小时显示"刚刚"或"x分钟前"
    if (diff < 60 * 1000) return tr('time.justNow')
    if (diff < 60 * 60 * 1000) {
      return tr('time.minutesAgo', { count: Math.floor(diff / 60000) })
    }

    // 今天显示时间
    if (date.toDateString() === now.toDateString()) {
      return date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
    }

    // 昨天
    const yesterday = new Date(now)
    yesterday.setDate(yesterday.getDate() - 1)
    if (date.toDateString() === yesterday.toDateString()) {
      const time = date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
      return tr('time.yesterdayAt', { time })
    }

    // 其他日期
    return (
      date.toLocaleDateString(locale, { month: 'numeric', day: 'numeric' }) +
      ' ' +
      date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
    )
  }

  // 初始化时加载历史
  loadHistory()

  return {
    historyList,
    filterKeyword,
    filteredHistory,
    addHistory,
    removeHistory,
    clearHistory,
    setFilterKeyword,
    formatTime,
    loadHistory
  }
})

function tr(key: string, params?: Record<string, unknown>): string {
  return i18n.global.t(key, params as Record<string, string>)
}
