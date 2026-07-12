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

function buildQueryString(params: any): string {
  if (!params) return ''
  const parts: string[] = []
  for (const key in params) {
    if (params.hasOwnProperty(key) && params[key] !== undefined && params[key] !== null) {
      parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(params[key]))
    }
  }
  return parts.join('&')
}

export async function request<T = any>(config: RequestConfig): Promise<ResponseData<T>> {
  return new Promise((resolve, reject) => {
    const userStr = uni.getStorageSync('user')
    let username = ''
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        username = user.username || ''
      } catch (e) {
        console.error('解析用户信息失败', e)
      }
    }
    
    const header: UniApp.RequestOptions['header'] = {
      'Content-Type': 'application/json',
      ...config.header
    }
    
    if (username) {
      header['X-Username'] = username
    }
    
    let url = baseURL + config.url
    
    if (config.params) {
      const queryString = buildQueryString(config.params)
      if (queryString) {
        url += '?' + queryString
      }
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
          uni.removeStorageSync('user')
          uni.redirectTo({ url: '/pages/login/index' })
          reject(new Error(data.message || '登录过期'))
        } else if (data.code === 404) {
          reject(new Error(data.message || '接口不存在或资源未找到'))
        } else if (data.code === 400) {
          reject(new Error(data.message || '参数错误'))
        } else if (data.code === 500) {
          reject(new Error(data.message || '服务器内部错误，请稍后重试'))
        } else {
          reject(new Error(data.message || '请求失败'))
        }
      },
      fail: (err) => {
        let msg = '网络请求失败'
        if (err.errMsg) {
          if (err.errMsg.includes('timeout')) {
            msg = '请求超时，请检查网络连接'
          } else if (err.errMsg.includes('fail') || err.errMsg.includes('error')) {
            msg = '网络连接失败，请检查后端服务是否启动'
          }
        }
        console.error('请求失败:', config.url, err)
        reject(new Error(msg))
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

export function put<T = any>(url: string, data?: any, params?: any): Promise<ResponseData<T>> {
  return request<T>({ url, method: 'PUT', data, params })
}

export function del<T = any>(url: string, params?: any): Promise<ResponseData<T>> {
  return request<T>({ url, method: 'DELETE', params })
}
