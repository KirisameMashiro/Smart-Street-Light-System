import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    lightDataVersion: 0
  }),
  actions: {
    notifyLightDataChanged() {
      this.lightDataVersion++
    }
  }
})
