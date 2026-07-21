import request from './request'

// 分页查询人流量历史数据
export function getPedestrianFlowPage(params) {
  return request.get('/pedestrian-flow/page', { params })
}

// 获取路灯最新人流量数据
export function getLatestPedestrianFlow(lightId) {
  return request.get(`/pedestrian-flow/latest/${lightId}`)
}

// 批量获取所有路灯最新人流量数据
export function getAllLatestPedestrianFlow() {
  return request.get('/pedestrian-flow/latest/all')
}

// 获取平均人流量数据
export function getAveragePedestrianFlow(lightId, startTime, endTime) {
  return request.get(`/pedestrian-flow/average/${lightId}`, {
    params: { startTime, endTime }
  })
}

// 新增人流量数据
export function addPedestrianFlow(data) {
  return request.post('/pedestrian-flow', data)
}