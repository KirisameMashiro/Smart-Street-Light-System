<template>
  <router-view />
  <AiFloatBall :visible="floatBallVisible" />
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import AiFloatBall from '@/components/AiFloatBall.vue'

const route = useRoute()
const floatBallVisible = ref(false)

function updateVisibility() {
  const enabled = localStorage.getItem('floatBallEnabled') === 'true'
  const isLogin = route.path === '/login'
  floatBallVisible.value = enabled && !isLogin
}

function onFloatBallToggle(e) {
  const enabled = e.detail
  const isLogin = route.path === '/login'
  floatBallVisible.value = enabled && !isLogin
}

onMounted(() => {
  updateVisibility()
  window.addEventListener('storage', updateVisibility)
  window.addEventListener('float-ball-toggle', onFloatBallToggle)
})

onUnmounted(() => {
  window.removeEventListener('storage', updateVisibility)
  window.removeEventListener('float-ball-toggle', onFloatBallToggle)
})

watch(
  () => route.path,
  () => {
    updateVisibility()
  }
)
</script>
