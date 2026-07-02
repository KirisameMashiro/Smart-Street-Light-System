<template>
  <div class="page-container assistant-container">
    <div class="page-header">
      <h2 class="page-title">AI 智能运维助手</h2>
      <el-button :icon="Refresh" :loading="historyLoading" @click="loadHistory">刷新历史</el-button>
    </div>

    <div class="chat-layout">
      <!-- 左侧会话列表（单会话简化） -->
      <div class="session-panel">
        <div class="session-title">会话列表</div>
        <div class="session-item active">
          <el-icon><ChatDotRound /></el-icon>
          <span class="session-name">当前会话</span>
        </div>
        <div class="session-id">ID：{{ shortSessionId }}</div>
        <div class="session-tip">
          单会话模式，历史记录通过 sessionId 持久化于本地，便于后端缺失时回看。
        </div>
      </div>

      <!-- 右侧聊天区 -->
      <div class="chat-panel">
        <!-- 快捷提问 -->
        <div class="quick-bar">
          <span class="quick-label">快捷提问：</span>
          <el-button
            v-for="q in quickQuestions"
            :key="q.label"
            size="small"
            round
            @click="onQuick(q.text)"
          >{{ q.label }}</el-button>
        </div>

        <!-- 消息区 -->
        <div ref="messageRef" class="message-area">
          <el-empty
            v-if="messages.length === 0"
            description="暂无对话，输入问题或点击快捷提问开始"
            :image-size="100"
          />
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="msg-row"
            :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
          >
            <div class="msg-avatar">
              <el-icon v-if="msg.role === 'user'"><User /></el-icon>
              <el-icon v-else><ChatDotRound /></el-icon>
            </div>
            <div class="msg-bubble">
              <div v-if="msg.loading" class="msg-loading">
                <el-icon class="loading-icon"><Loading /></el-icon>
                <span>思考中...</span>
              </div>
              <div v-else class="msg-text">{{ msg.content }}</div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入问题，回车发送（Shift+Enter 换行）"
            @keydown.enter="onEnter"
          />
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="sending"
            :disabled="!inputText.trim()"
            @click="onSend"
          >发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { Refresh, Promotion, ChatDotRound, User, Loading } from '@element-plus/icons-vue'
import { sendChatMessage, getChatHistory } from '@/api/assistant'

const SESSION_KEY = 'smartlight_ai_session'
const HISTORY_KEY = 'smartlight_ai_history'

const sessionId = ref(getOrCreateSession())
const inputText = ref('')
const sending = ref(false)
const historyLoading = ref(false)
const messages = ref([])
const messageRef = ref()

const shortSessionId = computed(() => sessionId.value.slice(-8))

const quickQuestions = [
  { label: '故障排查', text: '路灯离线或调光失败，如何排查？' },
  { label: '维护规范', text: '请介绍一下路灯日常维护规范。' },
  { label: '设备参数', text: 'LED 路灯常见设备参数有哪些？' },
  { label: '操作指引', text: '如何通过系统远程开关灯和调节亮度？' }
]

function getOrCreateSession() {
  let sid = localStorage.getItem(SESSION_KEY)
  if (!sid) {
    sid = 'sess_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
    localStorage.setItem(SESSION_KEY, sid)
  }
  return sid
}

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
    await nextTick()
    scrollToBottom()
  } catch (e) {
    // 后端缺失：尝试读取本地缓存
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
  } finally {
    historyLoading.value = false
  }
}

function onEnter(e) {
  if (e.shiftKey) return // Shift+Enter 换行
  e.preventDefault()
  onSend()
}

async function onSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  messages.value.push({ role: 'user', content: text, loading: false })
  inputText.value = ''
  await nextTick()
  scrollToBottom()

  messages.value.push({ role: 'ai', content: '', loading: true })
  const aiIdx = messages.value.length - 1
  sending.value = true
  await nextTick()
  scrollToBottom()

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
    await nextTick()
    scrollToBottom()
  }
}

function onQuick(text) {
  if (sending.value) return
  inputText.value = text
  onSend()
}

function cacheLocal() {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(messages.value))
  } catch (e) {
    // ignore quota errors
  }
}

function scrollToBottom() {
  if (messageRef.value) {
    messageRef.value.scrollTop = messageRef.value.scrollHeight
  }
}

onMounted(loadHistory)
</script>

<style scoped>
.chat-layout {
  display: flex;
  gap: 16px;
}

.session-panel {
  width: 220px;
  background: #fff;
  border-radius: 8px;
  box-shadow: var(--card-shadow);
  padding: 12px;
  flex-shrink: 0;
  align-self: flex-start;
}

.session-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  padding: 0 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: default;
  color: #303133;
}

.session-item.active {
  background: #ecf5ff;
  color: #409eff;
}

.session-name {
  font-size: 14px;
}

.session-id {
  margin-top: 8px;
  padding: 0 8px;
  font-size: 12px;
  color: #c0c4cc;
  word-break: break-all;
}

.session-tip {
  margin-top: 12px;
  padding: 8px;
  font-size: 12px;
  color: #c0c4cc;
  line-height: 1.5;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  box-shadow: var(--card-shadow);
  min-width: 0;
}

.quick-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: wrap;
}

.quick-label {
  color: #909399;
  font-size: 13px;
}

.message-area {
  height: 480px;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
}

.msg-row {
  display: flex;
  margin-bottom: 16px;
  gap: 8px;
}

.msg-user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #d9ecff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.msg-ai .msg-avatar {
  background: #e6e6e6;
  color: #606266;
}

.msg-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.msg-user .msg-bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}

.msg-ai .msg-bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
  border-top-left-radius: 2px;
}

.msg-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
}

.loading-icon {
  animation: ai-spin 1s linear infinite;
}

@keyframes ai-spin {
  to {
    transform: rotate(360deg);
  }
}

.input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  align-items: flex-end;
}

.input-area .el-input {
  flex: 1;
}
</style>
