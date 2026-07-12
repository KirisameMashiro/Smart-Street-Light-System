<template>
  <Teleport to="body">
    <Transition name="float-ball">
      <div
        v-if="visible"
        class="ai-float-ball"
        :class="{ 'is-expanded': expanded }"
        :style="{ left: position.x + 'px', top: position.y + 'px' }"
      >
        <div
          class="ball-main"
          @mousedown="onMouseDown"
          @touchstart.prevent="onTouchStart"
        >
          <el-icon class="ball-icon"><MagicStick /></el-icon>
          <span class="ball-label">AI</span>
        </div>

        <Transition name="panel-slide">
          <div v-if="expanded" class="ball-panel">
            <div
              class="panel-header"
              @mousedown.stop="onPanelMouseDown"
              @touchstart.prevent.stop="onPanelTouchStart"
            >
              <span class="panel-title">AI 助手</span>
              <el-icon class="panel-close" @click="expanded = false"><Close /></el-icon>
            </div>

            <div ref="messageRef" class="panel-messages">
              <el-empty
                v-if="chatStore.messages.length === 0"
                description="请输入问题开始对话"
                :image-size="60"
              />
              <div
                v-for="(msg, idx) in chatStore.messages"
                :key="idx"
                class="msg-row"
                :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
              >
                <div class="msg-bubble">
                  <div v-if="msg.loading" class="msg-loading">
                    <el-icon class="loading-icon"><Loading /></el-icon>
                    <span>思考中...</span>
                  </div>
                  <div v-else class="msg-text" v-html="renderMarkdown(msg.content)"></div>
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
                :loading="chatStore.sending"
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
import { useChatStore } from '@/store/chat'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const chatStore = useChatStore()
const expanded = ref(false)
const inputText = ref('')
const messageRef = ref()

const position = reactive({
  x: 0,
  y: 0
})

const quickQuestions = ['故障排查', '操作指引', '设备参数']

let isDragging = false
let dragStartX = 0
let dragStartY = 0
let startX = 0
let startY = 0
let ballWidth = 56
let ballHeight = 56
let historyLoaded = false

// 监听消息变化，自动滚动到底部
watch(() => chatStore.messages.length, () => {
  nextTick(() => scrollToBottom())
})

async function onSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.sending) return

  inputText.value = ''
  await chatStore.sendMessage(text)
  nextTick(() => scrollToBottom())
}

function onQuick(text) {
  if (chatStore.sending) return
  inputText.value = text
  onSend()
}

function scrollToBottom() {
  if (messageRef.value) {
    messageRef.value.scrollTop = messageRef.value.scrollHeight
  }
}

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

function clampPosition() {
  const maxX = window.innerWidth - ballWidth - 10
  const maxY = window.innerHeight - ballHeight - 10
  position.x = Math.max(10, Math.min(maxX, position.x))
  position.y = Math.max(10, Math.min(maxY, position.y))
}

function onMouseDown(e) {
  isDragging = false
  dragStartX = e.clientX
  dragStartY = e.clientY
  startX = position.x
  startY = position.y
  ballWidth = e.currentTarget.offsetWidth
  ballHeight = e.currentTarget.offsetHeight

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  document.addEventListener('mouseleave', onMouseUp)
}

function onMouseMove(e) {
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY

  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    isDragging = true
  }

  position.x = startX + dx
  position.y = startY + dy
  clampPosition()
}

function onMouseUp() {
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  document.removeEventListener('mouseleave', onMouseUp)

  if (!isDragging) {
    expanded.value = !expanded.value
    if (expanded.value && !historyLoaded) {
      historyLoaded = true
      chatStore.loadHistory().then(() => nextTick(() => scrollToBottom()))
    }
  }
  isDragging = false
}

function onTouchStart(e) {
  const touch = e.touches[0]
  isDragging = false
  dragStartX = touch.clientX
  dragStartY = touch.clientY
  startX = position.x
  startY = position.y

  document.addEventListener('touchmove', onTouchMove, { passive: false })
  document.addEventListener('touchend', onTouchEnd)
}

function onTouchMove(e) {
  e.preventDefault()
  const touch = e.touches[0]
  const dx = touch.clientX - dragStartX
  const dy = touch.clientY - dragStartY

  if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
    isDragging = true
  }

  position.x = startX + dx
  position.y = startY + dy
  clampPosition()
}

function onTouchEnd() {
  document.removeEventListener('touchmove', onTouchMove)
  document.removeEventListener('touchend', onTouchEnd)

  if (!isDragging) {
    expanded.value = !expanded.value
    if (expanded.value && !historyLoaded) {
      historyLoaded = true
      chatStore.loadHistory().then(() => nextTick(() => scrollToBottom()))
    }
  }
  isDragging = false
}

// ====== 对话框 header 拖动 ======
function onPanelMouseDown(e) {
  isDragging = false
  dragStartX = e.clientX
  dragStartY = e.clientY
  startX = position.x
  startY = position.y
  ballWidth = 56
  ballHeight = 56

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onPanelMouseUp)
  document.addEventListener('mouseleave', onPanelMouseUp)
}

function onPanelMouseUp() {
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onPanelMouseUp)
  document.removeEventListener('mouseleave', onPanelMouseUp)
  isDragging = false
}

function onPanelTouchStart(e) {
  const touch = e.touches[0]
  isDragging = false
  dragStartX = touch.clientX
  dragStartY = touch.clientY
  startX = position.x
  startY = position.y

  document.addEventListener('touchmove', onTouchMove, { passive: false })
  document.addEventListener('touchend', onPanelTouchEnd)
}

function onPanelTouchEnd() {
  document.removeEventListener('touchmove', onTouchMove)
  document.removeEventListener('touchend', onPanelTouchEnd)
  isDragging = false
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
      position.x = pos.x || window.innerWidth - 76
      position.y = pos.y || window.innerHeight - 140
    } catch (e) {
      position.x = window.innerWidth - 76
      position.y = window.innerHeight - 140
    }
  } else {
    position.x = window.innerWidth - 76
    position.y = window.innerHeight - 140
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
  cursor: grab;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
}

.ball-main:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.5);
}

.ball-main:active {
  cursor: grabbing;
  transform: scale(0.98);
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
  position: absolute;
  bottom: 70px;
  left: 50%;
  transform: translateX(-50%);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  flex-shrink: 0;
  cursor: grab;
  user-select: none;
}

.panel-header:active {
  cursor: grabbing;
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
  transform: translateX(-50%) translateY(10px);
}

@media (max-width: 768px) {
  .ball-panel {
    width: calc(100vw - 40px);
    height: 60vh;
    max-height: 500px;
    left: 20px;
    right: 20px;
    transform: none;
  }

  .panel-slide-enter-from,
  .panel-slide-leave-to {
    opacity: 0;
    transform: translateY(10px);
  }
}

/* Markdown 样式 */
.msg-text :deep(table) {
  border-collapse: collapse;
  margin: 6px 0;
  width: 100%;
  font-size: 12px;
}
.msg-text :deep(th),
.msg-text :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 4px 8px;
  text-align: left;
}
.msg-text :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}
.msg-text :deep(h1),
.msg-text :deep(h2),
.msg-text :deep(h3) {
  margin: 8px 0 4px;
  font-size: 14px;
}
.msg-text :deep(ul),
.msg-text :deep(ol) {
  padding-left: 18px;
  margin: 4px 0;
}
.msg-text :deep(code) {
  background: #f0f2f5;
  padding: 1px 3px;
  border-radius: 3px;
  font-size: 11px;
}
.msg-text :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding-left: 8px;
  margin: 6px 0;
  color: #606266;
}
</style>
