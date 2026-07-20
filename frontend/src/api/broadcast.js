import request from './request'

export function getBroadcasts() {
  return request.get('/broadcast/broadcasts')
}

export function getBroadcast(id) {
  return request.get(`/broadcast/broadcasts/${id}`)
}

export function addBroadcast(data) {
  return request.post('/broadcast/broadcasts', data)
}

export function updateBroadcast(data) {
  return request.put('/broadcast/broadcasts', data)
}

export function deleteBroadcast(id) {
  return request.delete(`/broadcast/broadcasts/${id}`)
}

export function getStrategies() {
  return request.get('/broadcast/strategies')
}

export function getStrategy(id) {
  return request.get(`/broadcast/strategies/${id}`)
}

export function addStrategy(data) {
  return request.post('/broadcast/strategies', data)
}

export function updateStrategy(data) {
  return request.put('/broadcast/strategies', data)
}

export function deleteStrategy(id) {
  return request.delete(`/broadcast/strategies/${id}`)
}