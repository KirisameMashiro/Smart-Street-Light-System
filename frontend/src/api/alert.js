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

// 获取未处理报警数量
export function getUnhandledCount() {
  return request.get('/alerts/unhandled-count')
}
