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
}

export async function loginApi(form: LoginForm) {
  const res = await post('/api/users/login', form)
  if (res.data) {
    uni.setStorageSync('user', JSON.stringify(res.data))
  }
  return res
}

export async function logoutApi() {
  uni.removeStorageSync('user')
}
