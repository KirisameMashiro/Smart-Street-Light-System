import request from './request'

// 照明控制 API —— 远程控制复用已有接口；定时策略/阈值联动 [后端缺失]
// 远程控制（复用 [后端已实现] 接口，由 control.js 统一封装日志记录）
export { batchSwitchLight, setLightBrightness } from './light.js'

// ============ 定时策略 [后端缺失] /api/control/strategies ============
export function getStrategyPage(params) {
  return request.get('/control/strategies/page', { params })
}
export function getStrategyList() {
  return request.get('/control/strategies')
}
export function addStrategy(data) {
  return request.post('/control/strategies', data)
}
export function updateStrategy(data) {
  return request.put('/control/strategies', data)
}
export function deleteStrategy(id) {
  return request.delete(`/control/strategies/${id}`)
}
export function toggleStrategy(id, enabled) {
  return request.put(`/control/strategies/${id}/enable`, null, {
    params: { enabled }
  })
}

// ============ 阈值联动控制 [后端缺失] /api/control/threshold ============
export function getThresholdConfig() {
  return request.get('/control/threshold')
}
export function updateThresholdConfig(data) {
  return request.put('/control/threshold', data)
}
// 一键启用/停用阈值联动
export function toggleThreshold(enabled) {
  return request.put('/control/threshold/toggle', null, { params: { enabled } })
}
