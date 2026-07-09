import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: string
  phone?: string
  email?: string
}

export const useUserStore = defineStore('user', () => {
  const user = ref<UserInfo | null>(null)

  function setUser(data: UserInfo) {
    user.value = data
    uni.setStorageSync('user', JSON.stringify(data))
  }

  function loadUser() {
    const str = uni.getStorageSync('user')
    if (str) {
      try {
        user.value = JSON.parse(str)
      } catch (e) {
        console.error('解析用户信息失败', e)
        user.value = null
      }
    }
    return user.value
  }

  function logout() {
    user.value = null
    uni.removeStorageSync('user')
  }

  return {
    user,
    setUser,
    loadUser,
    logout
  }
})
