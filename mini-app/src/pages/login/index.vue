<template>
  <view class="login-page">
    <view class="login-header">
      <view class="logo">
        <view class="logo-icon">
          <text class="icon-text">☀</text>
        </view>
        <text class="logo-title">智慧路灯</text>
        <text class="logo-subtitle">管理系统</text>
      </view>
    </view>
    
    <view class="login-form">
      <view class="form-item">
        <view class="form-label">用户名</view>
        <input 
          v-model="form.username" 
          class="form-input" 
          placeholder="请输入用户名"
          confirm-type="next"
        />
      </view>
      
      <view class="form-item">
        <view class="form-label">密码</view>
        <input 
          v-model="form.password" 
          class="form-input" 
          type="password"
          placeholder="请输入密码"
          confirm-type="done"
          @confirm="handleLogin"
        />
      </view>
      
      <button 
        class="login-btn" 
        :loading="loading"
        :disabled="loading || !form.username || !form.password"
        @click="handleLogin"
      >
        登录
      </button>
      
      <view class="tips">
        <text>测试账号：admin / 123456</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { loginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const form = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const userStore = useUserStore()

async function handleLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  
  loading.value = true
  try {
    const res = await loginApi(form)
    if (res.data) {
      userStore.setUser(res.data)
      uni.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/home/index' })
      }, 1000)
    }
  } catch (e: any) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0a1628 0%, #1a2f4a 100%);
  padding: 60rpx 40rpx;
}

.login-header {
  display: flex;
  justify-content: center;
  padding: 80rpx 0 60rpx;
}

.logo {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-icon {
  width: 120rpx;
  height: 120rpx;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 32rpx rgba(64, 158, 255, 0.4);
}

.icon-text {
  font-size: 60rpx;
}

.logo-title {
  font-size: 48rpx;
  font-weight: bold;
  color: #fff;
  margin-bottom: 8rpx;
}

.logo-subtitle {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.6);
}

.login-form {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24rpx;
  padding: 48rpx 40rpx;
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.3);
}

.form-item {
  margin-bottom: 40rpx;
}

.form-label {
  font-size: 28rpx;
  color: #333;
  margin-bottom: 16rpx;
}

.form-input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  border: 2rpx solid #e0e0e0;
  border-radius: 12rpx;
  font-size: 30rpx;
  background: #fafafa;
  
  &:focus {
    border-color: #409eff;
    background: #fff;
  }
}

.login-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff;
  font-size: 32rpx;
  font-weight: bold;
  border-radius: 48rpx;
  margin-top: 20rpx;
  border: none;
  
  &::after {
    border: none;
  }
  
  &[disabled] {
    opacity: 0.6;
  }
}

.tips {
  text-align: center;
  margin-top: 32rpx;
  font-size: 24rpx;
  color: #999;
}
</style>
