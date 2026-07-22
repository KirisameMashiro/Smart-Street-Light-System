import request from './request'

// ========== 广播设计管理 ==========

export function getBroadcasts() {
  return request.get('/broadcast/broadcasts')
}

export function getBroadcast(id) {
  return request.get(`/broadcast/broadcasts/${id}`)
}

export function addBroadcast(data) {
  return request.post('/broadcast/broadcasts', data)
}

export function updateBroadcast(data) {
  return request.put('/broadcast/broadcasts', data)
}

export function deleteBroadcast(id) {
  return request.delete(`/broadcast/broadcasts/${id}`)
}

// ========== 语音生成 ==========

/**
 * 为指定广播生成语音文件
 */
export function generateVoice(id) {
  return request.post(`/broadcast/broadcasts/${id}/generate-voice`, null, {
    timeout: 300000  // 5 分钟，TTS 模型首次加载较慢
  })
}

/**
 * 获取语音文件播放 URL
 */
export function getVoiceFileUrl(id) {
  return `/api/broadcast/broadcasts/${id}/voice-file`
}

/**
 * 检查广播是否关联了带监控的路灯
 */
export function getBroadcastMonitoring(id) {
  return request.get(`/broadcast/broadcasts/${id}/has-monitoring`)
}

// ========== 广播策略管理 ==========

export function getStrategies() {
  return request.get('/broadcast/strategies')
}

export function getStrategy(id) {
  return request.get(`/broadcast/strategies/${id}`)
}

export function addStrategy(data) {
  return request.post('/broadcast/strategies', data)
}

export function updateStrategy(data) {
  return request.put('/broadcast/strategies', data)
}

export function deleteStrategy(id) {
  return request.delete(`/broadcast/strategies/${id}`)
}

export function testPlayStrategy(id) {
  return request.post(`/broadcast/strategies/${id}/test-play`)
}
