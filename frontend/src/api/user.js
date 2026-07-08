import request from './request'

// 用户登录
export function login(data) {
  return request.post('/users/login', data)
}

// 获取用户列表
export function getUserList() {
  return request.get('/users')
}

// 新增用户
export function addUser(data) {
  return request.post('/users', data)
}

// 更新用户
export function updateUser(data) {
  return request.put('/users', data)
}

// 删除用户
export function deleteUser(id) {
  return request.delete(`/users/${id}`)
}

// 修改密码
export function changePassword(data) {
  return request.post('/users/change-password', data)
}
