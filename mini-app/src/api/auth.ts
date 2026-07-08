import { post } from './request'

export interface LoginForm {
  username: string
  password: string
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: string
  token: string
}

export async function loginApi(form: LoginForm) {
  const res = await post('/auth/login', form)
  if (res.data) {
    uni.setStorageSync('token', res.data.token)
    uni.setStorageSync('user', JSON.stringify(res.data))
  }
  return res
}

export async function logoutApi() {
  await post('/auth/logout')
  uni.removeStorageSync('token')
  uni.removeStorageSync('user')
}
