import request from './request'

// 系统参数与告警规则配置 API [后端缺失] /api/system/config、/api/system/alert-rules

// 全局系统参数
export function getSystemConfig() {
  return request.get('/system/config')
}
export function updateSystemConfig(data) {
  return request.put('/system/config', data)
}

// 告警规则
export function getAlertRules() {
  return request.get('/system/alert-rules')
}
export function updateAlertRule(data) {
  return request.put('/system/alert-rules', data)
}
export function addAlertRule(data) {
  return request.post('/system/alert-rules', data)
}
export function deleteAlertRule(id) {
  return request.delete(`/system/alert-rules/${id}`)
}
