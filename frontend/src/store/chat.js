import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sendChatMessage, getChatHistory } from '@/api/assistant'

/**
 * AI 聊天消息共享 Store
 * AiAssistant 页面和 AiFloatBall 悬浮球共用此 Store，
 * 实现消息实时同步：任一组件发送消息，另一个组件自动更新。
 */
export const useChatStore = defineStore('chat', () => {
  // ==================== 状态 ====================

  const SESSION_KEY = 'smartlight_ai_session'
  const HISTORY_KEY = 'smartlight_ai_history'

  const sessionId = ref(getOrCreateSession())
  const messages = ref([])
  const sending = ref(false)
  const historyLoading = ref(false)

  // ==================== Session ====================

  function getOrCreateSession() {
    let sid = localStorage.getItem(SESSION_KEY)
    if (!sid) {
      sid = 'sess_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
      localStorage.setItem(SESSION_KEY, sid)
    }
    return sid
  }

  // ==================== 历史记录 ====================

  function parseHistoryList(raw) {
    if (!raw) return []
    if (Array.isArray(raw)) return raw
    if (Array.isArray(raw.records)) return raw.records
    return []
  }

  async function loadHistory() {
    historyLoading.value = true
    try {
      const res = await getChatHistory(sessionId.value)
      const list = parseHistoryList(res?.data)
      messages.value = list.map((it) => ({
        role: it.role || (it.from === 'user' ? 'user' : 'ai'),
        content: it.content || it.message || it.reply || '',
        loading: false
      }))
      cacheLocal()
      return true
    } catch (e) {
      const cached = localStorage.getItem(HISTORY_KEY)
      if (cached) {
        try {
          messages.value = JSON.parse(cached)
        } catch (err) {
          messages.value = []
        }
      } else {
        messages.value = []
      }
      return false
    } finally {
      historyLoading.value = false
    }
  }

  // ==================== 发送消息 ====================

  async function sendMessage(text) {
    if (!text || sending.value) return

    messages.value.push({ role: 'user', content: text, loading: false })

    messages.value.push({ role: 'ai', content: '', loading: true })
    const aiIdx = messages.value.length - 1
    sending.value = true

    try {
      const res = await sendChatMessage({ sessionId: sessionId.value, message: text })
      const d = res?.data
      let reply = ''
      if (typeof d === 'string') {
        reply = d
      } else if (d && typeof d === 'object') {
        reply = d.reply || d.content || d.message || d.answer || ''
      }
      messages.value[aiIdx].content = reply || '(空回复)'
      messages.value[aiIdx].loading = false
    } catch (e) {
      messages.value[aiIdx].content = 'AI 服务暂未接入，请稍后再试。'
      messages.value[aiIdx].loading = false
    } finally {
      sending.value = false
      cacheLocal()
    }
  }

  // ==================== 本地缓存 ====================

  function cacheLocal() {
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(messages.value))
    } catch (e) {
      // ignore quota errors
    }
  }

  return {
    sessionId,
    messages,
    sending,
    historyLoading,
    loadHistory,
    sendMessage
  }
})
