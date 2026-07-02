import request from './request'

// AI 智能运维助手 API [后端缺失] /api/ai/assistant/*
// 后端实现方案：先本地知识库关键词匹配，匹配不到则调用大模型 API

// 发送对话消息（支持多轮）
export function sendChatMessage(data) {
  return request.post('/ai/assistant/chat', data)
}

// 获取历史会话列表
export function getChatHistory(sessionId) {
  return request.get('/ai/assistant/history', { params: { sessionId } })
}

// 本地知识库管理（FAQ 条目）
export function getKnowledgePage(params) {
  return request.get('/ai/assistant/knowledge', { params })
}
export function addKnowledge(data) {
  return request.post('/ai/assistant/knowledge', data)
}
export function updateKnowledge(data) {
  return request.put('/ai/assistant/knowledge', data)
}
export function deleteKnowledge(id) {
  return request.delete(`/ai/assistant/knowledge/${id}`)
}
