import request from './request'

// 分页查询传感器数据
export function getSensorDataPage(params) {
  return request.get('/sensor-data/page', { params })
}

// 获取路灯最新传感器数据
export function getLatestSensorData(lightId) {
  return request.get(`/sensor-data/latest/${lightId}`)
}

// 获取平均传感器数据
export function getAverageSensorData(lightId, startTime, endTime) {
  return request.get(`/sensor-data/average/${lightId}`, {
    params: { startTime, endTime }
  })
}

// 新增传感器数据
export function addSensorData(data) {
  return request.post('/sensor-data', data)
}

// 获取所有路灯今日累计耗电 (Wh)
export function getTodayEnergy() {
  return request.get('/sensor-data/today-energy')
}

// 批量获取所有路灯最新传感器数据
// 替代之前的逐盏查询，后端从 Redis pipeline 一次读取
export function getAllLatestSensorData() {
  return request.get('/sensor-data/latest/all')
}
