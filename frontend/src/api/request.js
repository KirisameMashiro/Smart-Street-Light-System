import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const service = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：附带当前登录用户标识
service.interceptors.request.use(
  (config) => {
    const userStr = localStorage.getItem('smartlight_user')
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        if (user?.username) {
          config.headers['X-Username'] = user.username
        }
      } catch (e) {
        // ignore
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理后端 Result 结构
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 非 Result 结构，原样返回
    if (res === null || typeof res !== 'object' || res.code === undefined) {
      return res
    }
    if (res.code === 200) {
      return res
    }
    // 401 未授权 / 登录失效
    if (res.code === 401) {
      ElMessage.error(res.message || '登录已失效，请重新登录')
      localStorage.removeItem('smartlight_user')
      router.push('/login')
      return Promise.reject(new Error(res.message || 'Unauthorized'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const status = error?.response?.status
    const url = error?.config?.url || ''
    // 404：后端接口缺失，给出明确提示便于定位
    if (status === 404) {
      const method = (error?.config?.method || 'get').toUpperCase()
      ElMessage.error(`后端接口缺失或未实现：${method} ${url}`)
      return Promise.reject(new Error(`后端接口缺失：${method} ${url}`))
    }
    const msg =
      error?.response?.data?.message ||
      error?.response?.statusText ||
      error.message ||
      '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
