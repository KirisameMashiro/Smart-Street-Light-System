import request from './request'

// 分页查询报警
export function getAlertPage(params) {
  return request.get('/alerts/page', { params })
}

// 获取报警详情
export function getAlertById(id) {
  return request.get(`/alerts/${id}`)
}

// 处理报警
export function handleAlert(id, data) {
  return request.put(`/alerts/${id}/handle`, data)
}

// 批量处理报警
export function handleAlertBatch(data) {
  return request.put('/alerts/handle-batch', data)
}

// 获取未处理报警数量
export function getUnhandledCount() {
  return request.get('/alerts/unhandled-count')
}

/**
 * 连接告警 WebSocket
 * 通过 Vite proxy /ws → 后端 ws://localhost:8080/ws/alert
 *
 * @param {Function} onMessage - 收到推送消息的回调 (data) => {}
 * @param {Function} onError   - 连接错误的回调 (error) => {}
 * @returns {WebSocket}
 */
export function connectAlertSocket(onMessage, onError) {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/alert`
  const ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('[AlertWS] WebSocket connected:', wsUrl)
  }

  ws.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data)
      if (payload.type === 'new_alert') {
        onMessage(payload.data)
      } else if (payload.type === 'merged_alert') {
        // 合并告警：传整个 data 对象 {total, unhandledCount, groups}
        onMessage(payload.data)
      }
    } catch (e) {
      console.error('[AlertWS] Message parse error:', e)
    }
  }

  ws.onerror = (error) => {
    console.error('[AlertWS] Connection error:', error)
    if (onError) onError(error)
  }

  ws.onclose = (event) => {
    console.log('[AlertWS] Disconnected, reconnect in 3s...', event.code, event.reason)
    setTimeout(() => {
      connectAlertSocket(onMessage, onError)
    }, 3000)
  }

  return ws
}
