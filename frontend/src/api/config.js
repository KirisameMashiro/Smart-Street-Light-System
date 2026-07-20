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

// 行政区管理
export function getDistricts() {
  return request.get('/system/districts')
}
export function addDistrict(data) {
  return request.post('/system/districts', data)
}
export function updateDistrict(data) {
  return request.put('/system/districts', data)
}
export function deleteDistrict(id) {
  return request.delete(`/system/districts/${id}`)
}

// 路段管理
export function getRoads() {
  return request.get('/system/roads')
}
export function addRoad(data) {
  return request.post('/system/roads', data)
}
export function updateRoad(data) {
  return request.put('/system/roads', data)
}
export function deleteRoad(id) {
  return request.delete(`/system/roads/${id}`)
}

// 设备类型管理
export function getDeviceTypes() {
  return request.get('/system/device-types')
}
export function addDeviceType(data) {
  return request.post('/system/device-types', data)
}
export function updateDeviceType(data) {
  return request.put('/system/device-types', data)
}
export function deleteDeviceType(id) {
  return request.delete(`/system/device-types/${id}`)
}
