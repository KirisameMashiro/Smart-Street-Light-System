import request from './request'

// AI 自适应预测调光 API [后端缺失] /api/ai/predict/*

// 获取未来 24 小时逐时推荐亮度
export function getPredictResult(lightId) {
  return request.get('/ai/predict/result', { params: { lightId } })
}

// 一键启用预测模式，自动下发调光指令
export function applyPredictMode(lightId) {
  return request.post('/ai/predict/apply', { lightId })
}

// 停用预测模式
export function stopPredictMode(lightId) {
  return request.post('/ai/predict/stop', { lightId })
}

// 效果对比：预测模式 vs 固定阈值模式能耗差异 + 预测准确率
export function getPredictCompare(params) {
  return request.get('/ai/predict/compare', { params })
}
