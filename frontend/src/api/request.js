import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const service = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：附带当前登录用户标识（后端未要求 token，这里作为身份传递）
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
    // 非 Result 结构（如直接返回二进制等），原样返回
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
    const msg = error?.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
