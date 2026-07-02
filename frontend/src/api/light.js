import request from './request'

// 分页查询路灯
export function getLightPage(params) {
  return request.get('/lights/page', { params })
}

// 获取所有路灯
export function getAllLights() {
  return request.get('/lights')
}

// 获取路灯详情
export function getLightById(id) {
  return request.get(`/lights/${id}`)
}

// 新增路灯
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

// 路灯状态统计
export function getLightStats() {
  return request.get('/lights/stats')
}
