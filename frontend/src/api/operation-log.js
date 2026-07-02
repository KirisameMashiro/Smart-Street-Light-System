import request from './request'

// 操作日志 API [后端缺失] /api/operation-logs
// 用于照明控制留痕 + 系统管理审计

// 分页查询操作日志（按用户、时间、类型筛选）
export function getOperationLogPage(params) {
  return request.get('/operation-logs/page', { params })
}

// 记录操作日志（前端在控制操作后调用，后端缺失则失败但不影响主流程）
export function addOperationLog(data) {
  return request.post('/operation-logs', data)
}
