import request from './request'

// 碳减排核算与可视化 API [后端缺失] /api/carbon/*

// 核心指标卡片：总节电量、总减排量、节能率
export function getCarbonSummary(params) {
  return request.get('/carbon/summary', { params })
}

// 能耗趋势折线图（月度每日/年度每月）
// type: monthly-月度每日 / yearly-年度每月
export function getCarbonTrend(params) {
  return request.get('/carbon/trend', { params })
}

// 路段对比柱状图
export function getCarbonRoadCompare(params) {
  return request.get('/carbon/road-compare', { params })
}

// 能耗基准配置（传统钠灯基准能耗，支持参数修改）
export function getEnergyBaseline() {
  return request.get('/carbon/baseline')
}
export function updateEnergyBaseline(data) {
  return request.put('/carbon/baseline', data)
}

// Excel 报表导出（返回 blob）
export function exportCarbonReport(params) {
  return request.get('/carbon/export', {
    params,
    responseType: 'blob'
  })
}
