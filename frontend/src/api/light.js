import request from './request'

// 路灯设备 API
// [后端已实现] 分页/列表/详情/增改删/批量开关/亮度/状态统计/下拉选项
// [后端缺失] 分组统计 group-stats、累计能耗 energy

// 分页查询路灯
export function getLightPage(params) {
  return request.get('/lights/page', { params })
}

// 获取所有路灯
export function getAllLights() {
  return request.get('/lights')
}

// 获取行政区列表
export function getDistricts() {
  return request.get('/lights/districts')
}

// 获取路段列表
export function getRoads() {
  return request.get('/lights/roads')
}

// 获取设备类型列表
export function getDeviceTypes() {
  return request.get('/lights/device-types')
}

// 获取路灯详情
export function getLightById(id) {
  return request.get(`/lights/${id}`)
}

// 新增路灯（含前端扩展字段 district/road/commissionDate/totalEnergy，后端实体缺失将忽略）
export function addLight(data) {
  return request.post('/lights', data)
}

// 更新路灯
export function updateLight(data) {
  return request.put('/lights', data)
}

// 删除路灯
export function deleteLight(id) {
  return request.delete(`/lights/${id}`)
}

// 批量开关灯
export function batchSwitchLight(ids, status) {
  return request.post('/lights/batch-switch', { ids, status })
}

// 设置路灯亮度
export function setLightBrightness(id, brightness) {
  return request.put(`/lights/${id}/brightness`, null, {
    params: { brightness }
  })
}

// 释放手动控制，恢复自动控制
export function releaseManualControl(id) {
  return request.put(`/lights/${id}/release-manual`)
}

// 批量释放手动控制，恢复自动控制
export function releaseManualControlBatch(ids) {
  return request.put('/lights/release-manual-batch', ids)
}

// 路灯状态统计（总数、在线/离线/故障）
export function getLightStats() {
  return request.get('/lights/stats')
}

// [后端缺失] 分组统计（按行政区/路段/类型）
export function getLightGroupStats(groupBy) {
  return request.get('/lights/group-stats', { params: { groupBy } })
}
