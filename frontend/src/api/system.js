import request from './request'

/**
 * 系统配置 / 基础数据 API
 * 从专用表（district / road / device_type）获取数据
 */

// 获取行政区列表（从专用表）
export function getSystemDistricts() {
  return request.get('/system/districts')
}

// 获取路段列表（从专用表，可选按行政区过滤）
export function getSystemRoads(districtId) {
  return request.get('/system/roads', { params: { districtId } })
}

// 获取设备类型列表（从专用表）
export function getSystemDeviceTypes() {
  return request.get('/system/device-types')
}