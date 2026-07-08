import type { UniApp } from '@dcloudio/types'

const baseURL = 'http://localhost:8080'

interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  params?: any
  header?: any
}

interface ResponseData<T = any> {
  code: number
  message: string
  data: T
}

export async function request<T = any>(config: RequestConfig): Promise<ResponseData<T>> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    
    const header: UniApp.RequestOptions['header'] = {
      'Content-Type': 'application/json',
      ...config.header
    }
    
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }
    
    let url = baseURL + config.url
    
    if (config.params) {
      const params = new URLSearchParams(config.params)
      url += '?' + params.toString()
    }
    
    uni.request({
      url,
      method: config.method || 'GET',
      data: config.data,
      header,
      timeout: 10000,
      success: (res) => {
        const data = res.data as ResponseData<T>
        if (data.code === 200 || data.code === 0) {
          resolve(data)
        } else if (data.code === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('user')
          uni.navigateTo({ url: '/pages/login/index' })
          reject(new Error('登录过期'))
        } else {
          reject(new Error(data.message || '请求失败'))
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络请求失败'))
      }
    })
  })
}

export function get<T = any>(url: string, params?: any): Promise<ResponseData<T>> {
  return request<T>({ url, method: 'GET', params })
}

export function post<T = any>(url: string, data?: any): Promise<ResponseData<T>> {
  return request<T>({ url, method: 'POST', data })
}

export function put<T = any>(url: string, data?: any): Promise<ResponseData<T>> {
  return request<T>({ url, method: 'PUT', data })
}

export function del<T = any>(url: string, params?: any): Promise<ResponseData<T>> {
  return request<T>({ url, method: 'DELETE', params })
}
