<template>
  <view class="home-page">
    <view class="header">
      <view class="greeting">
        <text class="greeting-text">你好，{{ userInfo.realName || userInfo.username }}</text>
        <text class="greeting-subtitle">欢迎来到智慧路灯管理系统</text>
      </view>
      <view class="avatar" @click="handleLogout">
        <text class="avatar-text">{{ userInfo.realName?.charAt(0) || userInfo.username?.charAt(0) }}</text>
      </view>
    </view>

    <view class="stats-section">
      <view class="stats-card">
        <view class="stat-icon total">
          <text>☀</text>
        </view>
        <view class="stat-info">
          <text class="stat-value">{{ stats.total }}</text>
          <text class="stat-label">路灯总数</text>
        </view>
      </view>
      <view class="stats-card">
        <view class="stat-icon online">
          <text>✓</text>
        </view>
        <view class="stat-info">
          <text class="stat-value">{{ stats.online }}</text>
          <text class="stat-label">在线</text>
        </view>
      </view>
      <view class="stats-card">
        <view class="stat-icon offline">
          <text>○</text>
        </view>
        <view class="stat-info">
          <text class="stat-value">{{ stats.offline }}</text>
          <text class="stat-label">离线</text>
        </view>
      </view>
      <view class="stats-card">
        <view class="stat-icon fault">
          <text>!</text>
        </view>
        <view class="stat-info">
          <text class="stat-value">{{ stats.fault }}</text>
          <text class="stat-label">故障</text>
        </view>
      </view>
    </view>

    <view class="quick-actions">
      <view class="section-title">快捷操作</view>
      <view class="action-grid">
        <view class="action-item" @click="navigateTo('/pages/monitor/index')">
          <view class="action-icon">
            <text>📊</text>
          </view>
          <text class="action-text">实时监控</text>
        </view>
        <view class="action-item" @click="navigateTo('/pages/control/index')">
          <view class="action-icon">
            <text>⚡</text>
          </view>
          <text class="action-text">远程控制</text>
        </view>
        <view class="action-item" @click="showToast('设备档案开发中')">
          <view class="action-icon">
            <text>📋</text>
          </view>
          <text class="action-text">设备档案</text>
        </view>
        <view class="action-item" @click="showToast('数据分析开发中')">
          <view class="action-icon">
            <text>📈</text>
          </view>
          <text class="action-text">数据分析</text>
        </view>
      </view>
    </view>

    <view class="recent-section">
      <view class="section-title">最近告警</view>
      <view class="recent-list">
        <view v-if="recentAlerts.length === 0" class="empty-tip">
          <text>暂无告警信息</text>
        </view>
        <view v-for="alert in recentAlerts" :key="alert.id" class="recent-item">
          <view class="alert-icon" :class="alert.type">
            <text>{{ alert.type === 'fault' ? '⚠' : '🔔' }}</text>
          </view>
          <view class="alert-info">
            <text class="alert-title">{{ alert.message }}</text>
            <text class="alert-time">{{ alert.time }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getLightStats } from '@/api/light'

const userStore = useUserStore()
const userInfo = ref(userStore.user)

const stats = ref({
  total: 0,
  online: 0,
  offline: 0,
  fault: 0
})

const recentAlerts = ref([
  { id: 1, type: 'fault', message: '人民路-001 路灯故障', time: '5分钟前' },
  { id: 2, type: 'warning', message: '中山路-005 亮度异常', time: '15分钟前' },
  { id: 3, type: 'fault', message: '和平路-003 通讯中断', time: '30分钟前' }
])

onMounted(() => {
  fetchStats()
})

async function fetchStats() {
  try {
    const res = await getLightStats()
    stats.value = res.data
  } catch (e) {
    console.error('获取统计数据失败', e)
  }
}

function navigateTo(url: string) {
  uni.navigateTo({ url })
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        uni.removeStorageSync('token')
        uni.removeStorageSync('user')
        uni.redirectTo({ url: '/pages/login/index' })
      }
    }
  })
}

function showToast(message: string) {
  uni.showToast({ title: message, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 120rpx;
}

.header {
  background: linear-gradient(135deg, #0a1628 0%, #1a2f4a 100%);
  padding: 60rpx 32rpx 48rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.greeting-text {
  font-size: 36rpx;
  font-weight: bold;
  color: #fff;
  display: block;
  margin-bottom: 8rpx;
}

.greeting-subtitle {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.6);
}

.avatar {
  width: 80rpx;
  height: 80rpx;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-text {
  font-size: 32rpx;
  color: #fff;
  font-weight: bold;
}

.stats-section {
  padding: 0 24rpx;
  margin-top: -40rpx;
}

.stats-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.stat-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  margin-right: 24rpx;
  
  &.total {
    background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  }
  
  &.online {
    background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  }
  
  &.offline {
    background: linear-gradient(135deg, #909399 0%, #a6a9ad 100%);
  }
  
  &.fault {
    background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
  }
}

.stat-value {
  font-size: 48rpx;
  font-weight: bold;
  color: #303133;
  display: block;
}

.stat-label {
  font-size: 24rpx;
  color: #909399;
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #303133;
  margin-bottom: 20rpx;
}

.quick-actions {
  padding: 32rpx 24rpx;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24rpx;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 0;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.action-icon {
  font-size: 48rpx;
  margin-bottom: 12rpx;
}

.action-text {
  font-size: 24rpx;
  color: #606266;
}

.recent-section {
  padding: 0 24rpx;
}

.recent-list {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.empty-tip {
  padding: 48rpx;
  text-align: center;
  color: #909399;
  font-size: 28rpx;
}

.recent-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:last-child {
    border-bottom: none;
  }
}

.alert-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  margin-right: 20rpx;
  
  &.fault {
    background: rgba(245, 108, 108, 0.1);
    color: #f56c6c;
  }
  
  &.warning {
    background: rgba(250, 173, 20, 0.1);
    color: #e6a23c;
  }
}

.alert-title {
  font-size: 28rpx;
  color: #303133;
  display: block;
  margin-bottom: 8rpx;
}

.alert-time {
  font-size: 24rpx;
  color: #909399;
}
</style>