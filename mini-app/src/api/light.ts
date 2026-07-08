import { get, post, put } from './request'

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
  return get<LightStats>('/lights/stats')
}

export async function getAllLights() {
  return get<Light[]>('/lights')
}

export async function getLightPage(params: {
  pageNum?: number
  pageSize?: number
  district?: string
  road?: string
  status?: number
}) {
  return get('/lights/page', params)
}

export async function getLightById(id: number) {
  return get<Light>(`/lights/${id}`)
}

export async function getLatestSensorData(lightId: number) {
  return get<SensorData>(`/sensor/latest/${lightId}`)
}

export async function switchLight(id: number, status: number) {
  return post(`/lights/${id}/switch`, { status })
}

export async function setLightBrightness(id: number, brightness: number) {
  return put(`/lights/${id}/brightness`, { brightness })
}

export async function getDistricts() {
  return get<string[]>('/lights/districts')
}

export async function getRoads() {
  return get<string[]>('/lights/roads')
}
