import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface User {
  id: number
  username: string
  realName: string
  role: string
  token: string
}

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  
  function setUser(data: User) {
    user.value = data
    uni.setStorageSync('user', JSON.stringify(data))
  }
  
  function getUser() {
    if (!user.value) {
      const stored = uni.getStorageSync('user')
      if (stored) {
        user.value = JSON.parse(stored)
      }
    }
    return user.value
  }
  
  function logout() {
    user.value = null
    uni.removeStorageSync('token')
    uni.removeStorageSync('user')
    uni.navigateTo({ url: '/pages/login/index' })
  }
  
  return { user, setUser, getUser, logout }
})
