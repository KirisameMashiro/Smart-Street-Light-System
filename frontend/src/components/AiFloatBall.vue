<template>
  <Teleport to="body">
    <Transition name="float-ball">
      <div
        v-if="visible"
        class="ai-float-ball"
        :class="{ 'is-expanded': expanded }"
        :style="{ bottom: position.bottom + 'px', right: position.right + 'px' }"
        @mousedown.stop="onDragStart"
        @touchstart.stop.passive="onTouchStart"
      >
        <div v-if="!expanded" class="ball-main" @click="toggleExpand">
          <el-icon class="ball-icon"><MagicStick /></el-icon>
          <span class="ball-label">AI</span>
        </div>

        <Transition name="panel-slide">
          <div v-if="expanded" class="ball-panel">
            <div class="panel-header">
              <span class="panel-title">AI 助手</span>
              <el-icon class="panel-close" @click.stop="toggleExpand"><Close /></el-icon>
            </div>

            <div ref="messageRef" class="panel-messages">
              <el-empty
                v-if="messages.length === 0"
                description="请输入问题开始对话"
                :image-size="60"
              />
              <div
                v-for="(msg, idx) in messages"
                :key="idx"
                class="msg-row"
                :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
              >
                <div class="msg-bubble">
                  <div v-if="msg.loading" class="msg-loading">
                    <el-icon class="loading-icon"><Loading /></el-icon>
                    <span>思考中...</span>
                  </div>
                  <div v-else class="msg-text">{{ msg.content }}</div>
                </div>
              </div>
            </div>

            <div class="panel-input">
              <el-input
                v-model="inputText"
                size="small"
                placeholder="输入问题，回车发送"
                @keydown.enter="onSend"
              />
              <el-button
                type="primary"
                size="small"
                :loading="sending"
                :disabled="!inputText.trim()"
                @click="onSend"
              >发送</el-button>
            </div>

            <div class="panel-quick">
              <span class="quick-label">快捷：</span>
              <el-button
                v-for="q in quickQuestions"
                :key="q"
                size="small"
                link
                type="primary"
                @click="onQuick(q)"
              >{{ q }}</el-button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MagicStick, Close, Loading } from '@element-plus/icons-vue'
import { sendChatMessage } from '@/api/assistant'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const expanded = ref(false)
const inputText = ref('')
const sending = ref(false)
const messages = ref([])
const messageRef = ref()

const SESSION_KEY = 'smartlight_ai_float_session'
const HISTORY_KEY = 'smartlight_ai_float_history'

const position = reactive({
  bottom: 80,
  right: 20
})

const quickQuestions = ['故障排查', '操作指引', '设备参数']

let dragStartX = 0
let dragStartY = 0
let startBottom = 0
let startRight = 0
let isDragging = false

function getSessionId() {
  let sid = localStorage.getItem(SESSION_KEY)
  if (!sid) {
    sid = 'float_sess_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
    localStorage.setItem(SESSION_KEY, sid)
  }
  return sid
}

function toggleExpand() {
  if (!isDragging) {
    expanded.value = !expanded.value
    if (expanded.value && messages.value.length === 0) {
      loadHistory()
    }
  }
}

function loadHistory() {
  const cached = localStorage.getItem(HISTORY_KEY)
  if (cached) {
    try {
      messages.value = JSON.parse(cached)
      nextTick(() => scrollToBottom())
    } catch (e) {
      messages.value = []
    }
  }
}

function cacheLocal() {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(messages.value))
  } catch (e) {}
}

function scrollToBottom() {
  if (messageRef.value) {
    messageRef.value.scrollTop = messageRef.value.scrollHeight
  }
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
    const res = await sendChatMessage({ sessionId: getSessionId(), message: text })
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

function onDragStart(e) {
  isDragging = false
  dragStartX = e.clientX
  dragStartY = e.clientY
  startBottom = position.bottom
  startRight = position.right
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e) {
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    isDragging = true
  }
  position.right = Math.max(10, startRight - dx)
  position.bottom = Math.max(10, startBottom + dy)
}

function onDragEnd() {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  setTimeout(() => {
    isDragging = false
  }, 50)
}

function onTouchStart(e) {
  const touch = e.touches[0]
  dragStartX = touch.clientX
  dragStartY = touch.clientY
  startBottom = position.bottom
  startRight = position.right
  isDragging = false
  document.addEventListener('touchmove', onTouchMove, { passive: false })
  document.addEventListener('touchend', onTouchEnd)
}

function onTouchMove(e) {
  e.preventDefault()
  const touch = e.touches[0]
  const dx = touch.clientX - dragStartX
  const dy = touch.clientY - dragStartY
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    isDragging = true
  }
  position.right = Math.max(10, startRight - dx)
  position.bottom = Math.max(10, startBottom + dy)
}

function onTouchEnd() {
  document.removeEventListener('touchmove', onTouchMove)
  document.removeEventListener('touchend', onTouchEnd)
  setTimeout(() => {
    if (!isDragging) {
      toggleExpand()
    }
    isDragging = false
  }, 50)
}

watch(
  () => route.path,
  () => {
    expanded.value = false
  }
)

onMounted(() => {
  const savedPos = localStorage.getItem('ai_float_position')
  if (savedPos) {
    try {
      const pos = JSON.parse(savedPos)
      position.bottom = pos.bottom || 80
      position.right = pos.right || 20
    } catch (e) {}
  }
})

onUnmounted(() => {
  localStorage.setItem('ai_float_position', JSON.stringify(position))
})
</script>

<style scoped>
.ai-float-ball {
  position: fixed;
  z-index: 9999;
  user-select: none;
}

.ball-main {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
}

.ball-main:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.5);
}

.ball-icon {
  font-size: 22px;
}

.ball-label {
  font-size: 10px;
  font-weight: 600;
  margin-top: -2px;
}

.ball-panel {
  width: 320px;
  height: 420px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  flex-shrink: 0;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
}

.panel-close {
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
}

.panel-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #fafafa;
}

.msg-row {
  margin-bottom: 12px;
  display: flex;
}

.msg-user {
  justify-content: flex-end;
}

.msg-bubble {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
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
  font-size: 13px;
}

.loading-icon {
  animation: float-spin 1s linear infinite;
}

@keyframes float-spin {
  to {
    transform: rotate(360deg);
  }
}

.panel-input {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.panel-input .el-input {
  flex: 1;
}

.panel-quick {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border-top: 1px solid #f0f0f0;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.quick-label {
  font-size: 12px;
  color: #909399;
}

.panel-quick .el-button {
  padding: 2px 6px;
  font-size: 12px;
}

.float-ball-enter-active,
.float-ball-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.float-ball-enter-from,
.float-ball-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

@media (max-width: 768px) {
  .ball-panel {
    width: calc(100vw - 40px);
    height: 60vh;
    max-height: 500px;
  }
}
</style>
