<template>
  <view class="login-page">
    <!-- 顶部背景 -->
    <view class="bg-decoration">
      <view class="circle circle-1"></view>
      <view class="circle circle-2"></view>
      <view class="circle circle-3"></view>
    </view>

    <!-- 顶部导航栏占位 -->
    <view class="status-bar" :style="{ height: statusBarHeight + 'px' }"></view>

    <!-- Logo 区域 -->
    <view class="header">
      <view class="logo-wrap">
        <view class="logo-bg"></view>
        <text class="logo-icon">☀</text>
      </view>
      <text class="app-name">智慧路灯</text>
      <text class="app-subtitle">智能管理系统</text>
    </view>

    <!-- 登录表单 -->
    <view class="form-card">
      <view class="form-title">
        <text class="title-text">欢迎登录</text>
        <text class="title-tip">请使用您的账号继续</text>
      </view>

      <view class="form-item">
        <view class="input-wrap" :class="{ focus: usernameFocus }">
          <text class="input-icon">👤</text>
          <input
            v-model="form.username"
            class="input"
            placeholder="请输入用户名"
            placeholder-class="placeholder"
            :maxlength="20"
            @focus="usernameFocus = true"
            @blur="usernameFocus = false"
          />
          <text v-if="form.username" class="clear-icon" @click="form.username = ''">✕</text>
        </view>
      </view>

      <view class="form-item">
        <view class="input-wrap" :class="{ focus: passwordFocus }">
          <text class="input-icon">🔒</text>
          <input
            v-model="form.password"
            class="input"
            :type="showPassword ? 'text' : 'password'"
            placeholder="请输入密码"
            placeholder-class="placeholder"
            :maxlength="20"
            @focus="passwordFocus = true"
            @blur="passwordFocus = false"
            @confirm="handleLogin"
          />
          <text class="toggle-eye" @click="showPassword = !showPassword">
            {{ showPassword ? '👁' : '👁‍🗨' }}
          </text>
        </view>
      </view>

      <view class="form-actions">
        <view class="remember-row" @click="rememberMe = !rememberMe">
          <view class="checkbox" :class="{ checked: rememberMe }">
            <text v-if="rememberMe">✓</text>
          </view>
          <text class="remember-text">记住我</text>
        </view>
        <text class="forgot-text" @click="handleForgot">忘记密码？</text>
      </view>

      <button
        class="login-btn"
        :class="{ active: form.username && form.password }"
        :loading="loading"
        :disabled="loading || !form.username || !form.password"
        @click="handleLogin"
      >
        <text v-if="!loading">登 录</text>
      </button>

      <view class="quick-login">
        <view class="divider">
          <view class="line"></view>
          <text class="divider-text">测试账号</text>
          <view class="line"></view>
        </view>
        <view class="quick-accounts">
          <view class="quick-item" @click="useAccount('admin', '123456')">
            <text class="quick-label">管理员</text>
            <text class="quick-value">admin</text>
          </view>
          <view class="quick-item" @click="useAccount('lisi', '123456')">
            <text class="quick-label">运维员</text>
            <text class="quick-value">lisi</text>
          </view>
        </view>
      </view>
    </view>

    <view class="footer">
      <text class="footer-text">© 2026 智慧路灯管理系统</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { loginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const form = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const rememberMe = ref(false)
const showPassword = ref(false)
const usernameFocus = ref(false)
const passwordFocus = ref(false)
const statusBarHeight = ref(20)
const userStore = useUserStore()

onMounted(() => {
  try {
    const sysInfo = uni.getSystemInfoSync()
    statusBarHeight.value = sysInfo.statusBarHeight || 20
  } catch (e) {
    console.error('获取系统信息失败', e)
  }

  // 读取记住的账号
  const saved = uni.getStorageSync('remember_account')
  if (saved) {
    form.username = saved
    rememberMe.value = true
  }
})

function useAccount(username: string, password: string) {
  form.username = username
  form.password = password
}

function handleForgot() {
  uni.showModal({
    title: '找回密码',
    content: '请联系系统管理员重置密码',
    showCancel: false
  })
}

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
      // 记住账号
      if (rememberMe.value) {
        uni.setStorageSync('remember_account', form.username)
      } else {
        uni.removeStorageSync('remember_account')
      }
      uni.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/home/index' })
      }, 800)
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
  background: linear-gradient(160deg, #0a1628 0%, #1a2f4a 50%, #2d4a6a 100%);
  position: relative;
  overflow: hidden;
  padding-bottom: 40rpx;
}

.bg-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.1;

  &.circle-1 {
    width: 500rpx;
    height: 500rpx;
    background: #409eff;
    top: -200rpx;
    right: -150rpx;
  }

  &.circle-2 {
    width: 300rpx;
    height: 300rpx;
    background: #67c23a;
    bottom: 200rpx;
    left: -100rpx;
  }

  &.circle-3 {
    width: 200rpx;
    height: 200rpx;
    background: #e6a23c;
    top: 40%;
    right: 20rpx;
  }
}

.status-bar {
  position: relative;
  z-index: 1;
}

.header {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0 60rpx;
}

.logo-wrap {
  width: 140rpx;
  height: 140rpx;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28rpx;
}

.logo-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 50%;
  box-shadow: 0 12rpx 40rpx rgba(64, 158, 255, 0.5);
}

.logo-icon {
  position: relative;
  z-index: 1;
  font-size: 80rpx;
  color: #fff;
}

.app-name {
  font-size: 56rpx;
  font-weight: bold;
  color: #fff;
  letter-spacing: 4rpx;
  margin-bottom: 12rpx;
  text-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.3);
}

.app-subtitle {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 6rpx;
}

.form-card {
  position: relative;
  z-index: 1;
  margin: 0 40rpx;
  background: #fff;
  border-radius: 32rpx;
  padding: 48rpx 40rpx 40rpx;
  box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.3);
}

.form-title {
  display: flex;
  flex-direction: column;
  margin-bottom: 48rpx;
}

.title-text {
  font-size: 44rpx;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8rpx;
}

.title-tip {
  font-size: 26rpx;
  color: #909399;
}

.form-item {
  margin-bottom: 28rpx;
}

.input-wrap {
  display: flex;
  align-items: center;
  height: 96rpx;
  background: #f5f7fa;
  border: 2rpx solid #ebeef5;
  border-radius: 16rpx;
  padding: 0 24rpx;
  transition: all 0.3s;

  &.focus {
    border-color: #409eff;
    background: #fff;
    box-shadow: 0 0 0 4rpx rgba(64, 158, 255, 0.1);
  }
}

.input-icon {
  font-size: 32rpx;
  margin-right: 16rpx;
}

.input {
  flex: 1;
  height: 96rpx;
  font-size: 30rpx;
  color: #303133;
}

.placeholder {
  color: #c0c4cc;
  font-size: 30rpx;
}

.clear-icon, .toggle-eye {
  font-size: 28rpx;
  color: #909399;
  padding: 12rpx;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40rpx;
  padding: 0 4rpx;
}

.remember-row {
  display: flex;
  align-items: center;
}

.checkbox {
  width: 32rpx;
  height: 32rpx;
  border: 2rpx solid #dcdfe6;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #fff;
  margin-right: 12rpx;

  &.checked {
    background: #409eff;
    border-color: #409eff;
  }
}

.remember-text {
  font-size: 26rpx;
  color: #606266;
}

.forgot-text {
  font-size: 26rpx;
  color: #409eff;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, #c0c4cc 0%, #909399 100%);
  color: #fff;
  font-size: 32rpx;
  font-weight: bold;
  border-radius: 48rpx;
  border: none;
  margin-bottom: 40rpx;
  transition: all 0.3s;

  &.active {
    background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
    box-shadow: 0 8rpx 20rpx rgba(64, 158, 255, 0.4);
  }

  &::after {
    border: none;
  }

  &[disabled] {
    opacity: 1;
  }
}

.quick-login {
  margin-top: 16rpx;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 28rpx;
}

.line {
  flex: 1;
  height: 1rpx;
  background: #ebeef5;
}

.divider-text {
  font-size: 24rpx;
  color: #909399;
  padding: 0 24rpx;
}

.quick-accounts {
  display: flex;
  gap: 20rpx;
}

.quick-item {
  flex: 1;
  background: #f5f7fa;
  border-radius: 16rpx;
  padding: 20rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 2rpx solid transparent;
  transition: all 0.2s;

  &:active {
    border-color: #409eff;
    background: #ecf5ff;
  }
}

.quick-label {
  font-size: 24rpx;
  color: #909399;
  margin-bottom: 6rpx;
}

.quick-value {
  font-size: 28rpx;
  color: #303133;
  font-weight: 500;
}

.footer {
  position: relative;
  z-index: 1;
  text-align: center;
  margin-top: 60rpx;
}

.footer-text {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.5);
  letter-spacing: 2rpx;
}
</style>
