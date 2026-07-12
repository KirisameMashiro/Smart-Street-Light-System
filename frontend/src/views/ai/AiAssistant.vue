<template>
  <div class="page-container assistant-container">
    <div class="page-header">
      <h2 class="page-title">AI 智能运维助手</h2>
      <el-button :icon="Refresh" :loading="chatStore.historyLoading" @click="chatStore.loadHistory">刷新历史</el-button>
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
          与 AI 悬浮球共享消息，两端发送的内容会实时同步。
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
            v-if="chatStore.messages.length === 0"
            description="暂无对话，输入问题或点击快捷提问开始"
            :image-size="100"
          />
          <div
            v-for="(msg, idx) in chatStore.messages"
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
              <div v-else class="msg-text" v-html="renderMarkdown(msg.content)"></div>
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
            :loading="chatStore.sending"
            :disabled="!inputText.trim()"
            @click="onSend"
          >发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'AiAssistant' })
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { Refresh, Promotion, ChatDotRound, User, Loading } from '@element-plus/icons-vue'
import { useChatStore } from '@/store/chat'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

const chatStore = useChatStore()
const inputText = ref('')
const messageRef = ref()

const shortSessionId = computed(() => chatStore.sessionId.slice(-8))

const quickQuestions = [
  { label: '故障排查', text: '路灯离线或调光失败，如何排查？' },
  { label: '维护规范', text: '请介绍一下路灯日常维护规范。' },
  { label: '设备参数', text: 'LED 路灯常见设备参数有哪些？' },
  { label: '操作指引', text: '如何通过系统远程开关灯和调节亮度？' }
]

// 监听消息变化，自动滚动到底部
watch(() => chatStore.messages.length, () => {
  nextTick(() => scrollToBottom())
})

function onEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  onSend()
}

async function onSend() {
  const text = inputText.value.trim()
  if (!text) return

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

onMounted(() => chatStore.loadHistory())
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
  color: #909399;
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

/* Markdown 样式 */
.msg-text :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}

.msg-text :deep(th),
.msg-text :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  text-align: left;
  font-size: 13px;
}

.msg-text :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}

.msg-text :deep(h1),
.msg-text :deep(h2),
.msg-text :deep(h3) {
  margin: 10px 0 6px;
  font-size: 15px;
}

.msg-text :deep(ul),
.msg-text :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}

.msg-text :deep(code) {
  background: #f0f2f5;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}

.msg-text :deep(pre) {
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
  overflow-x: auto;
}

.msg-text :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding-left: 10px;
  margin: 8px 0;
  color: #606266;
}
</style>
