import { defineStore } from 'pinia'

const STORAGE_KEY = 'smartlight_user'

function loadUser() {
  try {
    const str = localStorage.getItem(STORAGE_KEY)
    return str ? JSON.parse(str) : null
  } catch (e) {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    user: loadUser()
  }),
  getters: {
    isLogin: (state) => !!state.user,
    isAdmin: (state) => state.user?.role === 'admin',
    displayName: (state) =>
      state.user?.realName || state.user?.username || '用户'
  },
  actions: {
    setUser(user) {
      this.user = user
      if (user) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
      } else {
        localStorage.removeItem(STORAGE_KEY)
      }
    },
    logout() {
      this.setUser(null)
    }
  }
})
