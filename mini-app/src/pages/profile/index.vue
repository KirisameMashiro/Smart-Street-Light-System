<template>
  <view class="profile-page">
    <view class="header-bg"></view>
    
    <view class="user-card">
      <view class="avatar-wrap">
        <view class="avatar">
          <text class="avatar-text">{{ avatarText }}</text>
        </view>
        <view class="online-dot"></view>
      </view>
      <view class="user-info">
        <text class="user-name">{{ userInfo.realName || userInfo.username }}</text>
        <view class="user-meta">
          <text class="role-tag">{{ roleText }}</text>
          <text class="user-id">ID: {{ userInfo.id }}</text>
        </view>
        <view class="contact-info">
          <text v-if="userInfo.phone" class="contact-item">📱 {{ userInfo.phone }}</text>
          <text v-if="userInfo.email" class="contact-item">📧 {{ userInfo.email }}</text>
        </view>
      </view>
      <view class="edit-btn" @click="goEdit">
        <text>编辑</text>
      </view>
    </view>

    <view class="stats-section">
      <view class="stat-item" @click="navigateTo('/pages/logs/index')">
        <view class="stat-icon-wrap">
          <text>📝</text>
        </view>
        <text class="stat-num">{{ stats.operateCount }}</text>
        <text class="stat-label">今日操作</text>
      </view>
      <view class="stat-item" @click="navigateTo('/pages/alerts/index')">
        <view class="stat-icon-wrap">
          <text>🔔</text>
        </view>
        <text class="stat-num">{{ stats.alertCount }}</text>
        <text class="stat-label">未处理告警</text>
        <view v-if="stats.alertCount > 0" class="stat-badge"></view>
      </view>
      <view class="stat-item">
        <view class="stat-icon-wrap">
          <text>💡</text>
        </view>
        <text class="stat-num">{{ stats.onlineCount }}</text>
        <text class="stat-label">在线设备</text>
      </view>
      <view class="stat-item">
        <view class="stat-icon-wrap">
          <text>🌱</text>
        </view>
        <text class="stat-num">{{ stats.totalCount }}</text>
        <text class="stat-label">设备总数</text>
      </view>
    </view>

    <view class="menu-section">
      <view class="menu-group">
        <text class="group-title">常用功能</text>
        <view class="menu-list">
          <view class="menu-item" @click="navigateTo('/pages/control/index')">
            <view class="menu-icon" style="background: rgba(64, 158, 255, 0.1);">
              <text>⚡</text>
            </view>
            <text class="menu-text">远程控制</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item" @click="navigateTo('/pages/strategy/index')">
            <view class="menu-icon" style="background: rgba(103, 194, 58, 0.1);">
              <text>⏰</text>
            </view>
            <text class="menu-text">定时策略</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item" @click="navigateTo('/pages/threshold/index')">
            <view class="menu-icon" style="background: rgba(230, 162, 60, 0.1);">
              <text>📊</text>
            </view>
            <text class="menu-text">阈值联动</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item" @click="navigateTo('/pages/carbon/index')">
            <view class="menu-icon" style="background: rgba(16, 185, 129, 0.1);">
              <text>🌱</text>
            </view>
            <text class="menu-text">碳减排分析</text>
            <text class="menu-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="menu-group">
        <text class="group-title">系统管理</text>
        <view class="menu-list">
          <view class="menu-item" @click="navigateTo('/pages/alerts/index')">
            <view class="menu-icon" style="background: rgba(245, 108, 108, 0.1);">
              <text>🔔</text>
            </view>
            <text class="menu-text">告警管理</text>
            <text v-if="stats.alertCount > 0" class="badge">{{ stats.alertCount }}</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item" @click="navigateTo('/pages/logs/index')">
            <view class="menu-icon" style="background: rgba(144, 147, 153, 0.1);">
              <text>📋</text>
            </view>
            <text class="menu-text">操作日志</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item" @click="navigateTo('/pages/profile/password')">
            <view class="menu-icon" style="background: rgba(64, 158, 255, 0.1);">
              <text>🔑</text>
            </view>
            <text class="menu-text">修改密码</text>
            <text class="menu-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="menu-group">
        <text class="group-title">其他</text>
        <view class="menu-list">
          <view class="menu-item" @click="checkUpdate">
            <view class="menu-icon" style="background: rgba(103, 194, 58, 0.1);">
              <text>🔄</text>
            </view>
            <text class="menu-text">检查更新</text>
            <text class="menu-value">v1.0.0</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item" @click="showAbout">
            <view class="menu-icon" style="background: rgba(230, 162, 60, 0.1);">
              <text>ℹ️</text>
            </view>
            <text class="menu-text">关于系统</text>
            <text class="menu-arrow">›</text>
          </view>
        </view>
      </view>
    </view>

    <view class="logout-wrap">
      <button class="logout-btn" @click="handleLogout">退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  getLightStats,
  getUnhandledAlertCount,
  getOperationLogs
} from '@/api/light'

const userStore = useUserStore()
const userInfo = computed(() => userStore.user || ({} as any))

const stats = ref({
  alertCount: 0,
  onlineCount: 0,
  totalCount: 0,
  operateCount: 0
})

const avatarText = computed(() => {
  const name = userInfo.value.realName || userInfo.value.username || 'U'
  return name.charAt(0).toUpperCase()
})

const roleText = computed(() => {
  const roleMap: Record<string, string> = {
    admin: '系统管理员',
    operator: '运维人员',
    viewer: '普通用户'
  }
  return roleMap[userInfo.value.role] || '用户'
})

onMounted(() => {
  userStore.loadUser()
  loadStats()
})

async function loadStats() {
  try {
    const [lightRes, alertRes, logRes] = await Promise.all([
      getLightStats(),
      getUnhandledAlertCount(),
      getOperationLogs({ pageNum: 1, pageSize: 50 })
    ])
    stats.value.onlineCount = lightRes.data?.online || 0
    stats.value.totalCount = lightRes.data?.total || 0
    stats.value.alertCount = alertRes.data || 0
    const today = new Date().toISOString().slice(0, 10)
    const records = logRes.data?.records || []
    stats.value.operateCount = records.filter((r: any) =>
      r.createTime && r.createTime.startsWith(today)
    ).length
  } catch (e) {
    console.error('加载统计数据失败', e)
  }
}

function navigateTo(url: string) {
  uni.navigateTo({ url })
}

function goEdit() {
  uni.showToast({ title: '请在网页端修改个人信息', icon: 'none' })
}

function checkUpdate() {
  uni.showToast({ title: '已是最新版本', icon: 'success' })
}

function showAbout() {
  uni.showModal({
    title: '关于系统',
    content: '智慧路灯管理系统 v1.0.0\n\n基于 Uni-app + Vue 3 开发\n支持微信小程序与 H5 多端运行',
    showCancel: false
  })
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        uni.reLaunch({ url: '/pages/login/index' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 420rpx;
  background: linear-gradient(135deg, #0a1628 0%, #1a2f4a 100%);
}

.user-card {
  position: relative;
  margin: 40rpx 24rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 32rpx;
  display: flex;
  align-items: flex-start;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.08);
}

.avatar-wrap {
  position: relative;
  margin-right: 24rpx;
}

.avatar {
  width: 130rpx;
  height: 130rpx;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(64, 158, 255, 0.4);
}

.avatar-text {
  font-size: 60rpx;
  color: #fff;
  font-weight: bold;
}

.online-dot {
  position: absolute;
  bottom: 4rpx;
  right: 4rpx;
  width: 24rpx;
  height: 24rpx;
  background: #67c23a;
  border-radius: 50%;
  border: 4rpx solid #fff;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 40rpx;
  font-weight: bold;
  color: #303133;
  display: block;
  margin-bottom: 12rpx;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.role-tag {
  font-size: 24rpx;
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
}

.user-id {
  font-size: 24rpx;
  color: #909399;
}

.contact-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.contact-item {
  font-size: 24rpx;
  color: #909399;
}

.edit-btn {
  padding: 16rpx 28rpx;
  background: rgba(64, 158, 255, 0.1);
  border-radius: 28rpx;
  border: 1rpx solid rgba(64, 158, 255, 0.2);
}

.edit-btn text {
  font-size: 26rpx;
  color: #409eff;
  font-weight: 500;
}

.stats-section {
  margin: 0 24rpx 32rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 32rpx 0;
  display: flex;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.stat-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  background: #f5f7fa;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
  font-size: 32rpx;
}

.stat-num {
  font-size: 40rpx;
  font-weight: bold;
  color: #303133;
  margin-bottom: 6rpx;
}

.stat-label {
  font-size: 22rpx;
  color: #909399;
}

.stat-badge {
  position: absolute;
  top: 0;
  right: 20rpx;
  width: 16rpx;
  height: 16rpx;
  background: #f56c6c;
  border-radius: 50%;
}

.menu-section {
  padding: 0 24rpx;
}

.menu-group {
  background: #fff;
  border-radius: 20rpx;
  margin-bottom: 24rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.group-title {
  display: block;
  font-size: 24rpx;
  color: #909399;
  padding: 24rpx 32rpx 16rpx;
}

.menu-list {
  display: flex;
  flex-direction: column;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #f5f7fa;

  &:last-child {
    border-bottom: none;
  }

  &:active {
    background: #fafafa;
  }
}

.menu-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.menu-icon text {
  font-size: 36rpx;
}

.menu-text {
  flex: 1;
  font-size: 30rpx;
  color: #303133;
}

.menu-arrow {
  font-size: 32rpx;
  color: #c0c4cc;
  font-weight: 300;
}

.menu-value {
  font-size: 24rpx;
  color: #909399;
  margin-right: 8rpx;
}

.badge {
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 12rpx;
  background: #f56c6c;
  color: #fff;
  font-size: 22rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
}

.logout-wrap {
  padding: 0 24rpx;
  margin-top: 24rpx;
}

.logout-btn {
  width: 100%;
  height: 100rpx;
  background: #fff;
  color: #f56c6c;
  font-size: 32rpx;
  font-weight: 500;
  border-radius: 50rpx;
  border: none;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);

  &::after {
    border: none;
  }
}
</style>