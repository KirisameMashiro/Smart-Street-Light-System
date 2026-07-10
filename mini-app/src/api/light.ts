import { get, post, put, del } from './request'

// ============ 路灯相关接口 ============
export interface Light {
  id: number
  lightCode: string
  lightName: string
  location: string
  longitude: number
  latitude: number
  status: number
  brightness: number
  deviceType: string
  ratedPower: number
  district: string
  road: string
}

export interface LightStats {
  total: number
  online: number
  offline: number
  fault: number
}

export interface SensorData {
  illuminance: number
  power: number
  voltage: number
  current: number
  temperature: number
  humidity: number
  samplingEnergy: number
}

export async function getLightStats() {
  return get<LightStats>('/api/lights/stats')
}

export async function getAllLights() {
  return get<Light[]>('/api/lights')
}

export async function getLightPage(params: {
  pageNum?: number
  pageSize?: number
  district?: string
  road?: string
  status?: number
  keyword?: string
}) {
  return get('/api/lights/page', params)
}

export async function getLightById(id: number) {
  return get<Light>(`/api/lights/${id}`)
}

export async function getLatestSensorData(lightId: number) {
  return get<SensorData>(`/api/sensor-data/latest/${lightId}`)
}

export async function getAverageSensorData(lightId: number) {
  return get<SensorData>(`/api/sensor-data/average/${lightId}`)
}

export async function switchLight(id: number, status: number) {
  // 后端暂无单灯开关接口，使用批量开关接口兼容
  return post('/api/lights/batch-switch', { ids: [id], status })
}

export async function setLightBrightness(id: number, brightness: number) {
  return put(`/api/lights/${id}/brightness`, null, { brightness })
}

export async function batchSwitch(ids: number[], status: number) {
  return post('/api/lights/batch-switch', { ids, status })
}

export async function getDistricts() {
  return get<string[]>('/api/lights/districts')
}

export async function getRoads() {
  return get<string[]>('/api/lights/roads')
}

// ============ 告警相关接口 ============
export interface Alert {
  id: number
  lightId: number
  lightCode: string
  alertType: string
  alertLevel: string
  alertMessage: string
  status: number
  createTime: string
  handleTime?: string
  handler?: string
  handleRemark?: string
}

export async function getRecentAlerts() {
  return get<{ records: Alert[]; total: number }>('/api/alerts/page', { pageNum: 1, pageSize: 10 })
}

export async function getAlertPage(params: {
  pageNum?: number
  pageSize?: number
  lightId?: number
  alertType?: string
  alertLevel?: string
  status?: number
}) {
  return get('/api/alerts/page', params)
}

export async function getAlertDetail(id: number) {
  return get<Alert>(`/api/alerts/${id}`)
}

export async function handleAlert(id: number, data: { handler: string; handleRemark: string }) {
  return put(`/api/alerts/${id}/handle`, data)
}

export async function getUnhandledAlertCount() {
  return get<number>('/api/alerts/unhandled-count')
}

// ============ 报警规则接口 ============
export interface AlertRule {
  id: number
  ruleName: string
  alertType: string
  condition: string
  threshold: number
  level: string
  enabled: boolean
  description?: string
}

export async function getAlertRules() {
  return get<AlertRule[]>('/api/system/alert-rules')
}

export async function addAlertRule(rule: Partial<AlertRule>) {
  return post('/api/system/alert-rules', rule)
}

export async function updateAlertRule(rule: AlertRule) {
  return put('/api/system/alert-rules', rule)
}

export async function deleteAlertRule(id: number) {
  return del(`/api/system/alert-rules/${id}`)
}

// ============ 定时策略接口 ============
export interface TimedStrategy {
  id?: number
  name: string
  type: string
  startDate?: string
  endDate?: string
  startTime: string
  endTime: string
  weekdays?: number[]
  district: string
  road: string
  brightness: number
  enabled: boolean
}

export async function getStrategyList() {
  return get<TimedStrategy[]>('/api/control/strategies')
}

export async function getEnabledStrategies() {
  return get<TimedStrategy[]>('/api/control/strategies/enabled')
}

export async function getStrategyPage(params: {
  pageNum?: number
  pageSize?: number
  type?: string
  name?: string
}) {
  return get('/api/control/strategies/page', params)
}

export async function addStrategy(strategy: TimedStrategy) {
  return post('/api/control/strategies', strategy)
}

export async function updateStrategy(strategy: TimedStrategy) {
  return put('/api/control/strategies', strategy)
}

export async function deleteStrategy(id: number) {
  return del(`/api/control/strategies/${id}`)
}

export async function toggleStrategy(id: number, enabled: boolean) {
  return put(`/api/control/strategies/${id}/enable`, null, { enabled })
}

// ============ 阈值联动接口 ============
export interface ThresholdControl {
  id?: number
  enabled: boolean
  illuminanceThreshold: number
  targetBrightness: number
  triggerTime: number
}

export async function getThreshold() {
  return get<ThresholdControl>('/api/control/threshold')
}

export async function updateThreshold(data: ThresholdControl) {
  return put('/api/control/threshold', data)
}

export async function toggleThreshold(enabled: boolean) {
  return put('/api/control/threshold/toggle', null, { enabled })
}

// ============ 碳减排接口 ============
export interface CarbonSummary {
  savedEnergy: number
  reducedCo2: number
  energySavingRate: number
}

export interface CarbonTrend {
  date: string
  savedEnergy: number
  reducedCo2: number
}

export interface RoadCompare {
  road: string
  savedEnergy: number
  district?: string
}

export async function getCarbonSummary(type?: string, period?: string) {
  return get<CarbonSummary>('/api/carbon/summary', { type, period })
}

export async function getCarbonTrend(type: string = 'month', period?: string) {
  return get<CarbonTrend[]>('/api/carbon/trend', { type, period })
}

export async function getRoadCompare(type?: string, period?: string) {
  return get<RoadCompare[]>('/api/carbon/road-compare', { type, period })
}

// ============ 操作日志接口 ============
export interface OperationLog {
  id: number
  operator: string
  type: string
  target: string
  detail: string
  ip: string
  createTime: string
}

export async function getOperationLogs(params: {
  pageNum?: number
  pageSize?: number
  operator?: string
  type?: string
  startTime?: string
  endTime?: string
}) {
  return get<{ records: OperationLog[]; total: number }>('/api/operation-logs/page', params)
}

// ============ 用户相关接口 ============
export interface User {
  id: number
  username: string
  realName: string
  role: string
  phone?: string
  email?: string
  createTime?: string
}

export async function getUserProfile() {
  return get<User>('/api/users/profile')
}

export async function updateProfile(data: Partial<User>) {
  return put('/api/users/profile', data)
}

export async function changePassword(data: { oldPassword: string; newPassword: string }) {
  return post('/api/users/change-password', data)
}
