<template>
  <view class="alerts-page">
    <!-- 统计卡片 -->
    <view class="stats-row">
      <view class="stat-card unhandled" @click="filterByStatus(0)">
        <text class="stat-num">{{ unhandledCount }}</text>
        <text class="stat-label">未处理</text>
      </view>
      <view class="stat-card handled" @click="filterByStatus(1)">
        <text class="stat-num">{{ handledCount }}</text>
        <text class="stat-label">已处理</text>
      </view>
      <view class="stat-card total" @click="filterByStatus(-1)">
        <text class="stat-num">{{ totalCount }}</text>
        <text class="stat-label">总数</text>
      </view>
    </view>

    <!-- 筛选区 -->
    <view class="filter-section">
      <view class="filter-tabs">
        <view
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-item"
          :class="{ active: currentLevel === tab.value }"
          @click="onLevelChange(tab.value)"
        >
          {{ tab.label }}
        </view>
      </view>
      <view class="type-filter">
        <picker mode="selector" :range="typeOptions" @change="onTypeChange" :value="typeIndex">
          <view class="type-picker">
            <text>{{ typeOptions[typeIndex] }}</text>
            <text class="picker-arrow">▼</text>
          </view>
        </picker>
      </view>
    </view>

    <!-- 告警列表 -->
    <view class="alert-list">
      <view v-if="loading && alertList.length === 0" class="loading-tip">
        <text>加载中...</text>
      </view>
      <view v-else-if="alertList.length === 0" class="empty-tip">
        <text class="empty-icon">📭</text>
        <text class="empty-text">暂无告警数据</text>
      </view>
      <view
        v-for="alert in alertList"
        :key="alert.id"
        class="alert-item"
        :class="['level-' + getLevelClass(alert.alertLevel)]"
        @click="goDetail(alert)"
      >
        <view class="alert-header">
          <view class="level-badge" :class="getLevelClass(alert.alertLevel)">
            <text>{{ getLevelIcon(alert.alertLevel) }}</text>
          </view>
          <view class="alert-title">
            <text class="title-text">{{ alert.message }}</text>
            <text class="title-time">{{ formatTime(alert.createTime) }}</text>
          </view>
          <view v-if="alert.status === 0" class="status-dot unhandled"></view>
          <view v-else class="status-dot handled"></view>
        </view>
        <view class="alert-content">
          <view class="info-row">
            <text class="info-label">类型：</text>
            <text class="info-value">{{ getTypeText(alert.alertType) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">路灯ID：</text>
            <text class="info-value">{{ alert.lightId || '-' }}</text>
          </view>
          <view v-if="alert.handler" class="info-row">
            <text class="info-label">处理人：</text>
            <text class="info-value">{{ alert.handler }}</text>
          </view>
        </view>
        <view class="alert-footer">
          <view v-if="alert.status === 0" class="action-btn primary" @click.stop="handleAlert(alert)">
            立即处理
          </view>
          <view v-else class="status-text">已处理</view>
          <view class="action-btn">查看详情</view>
        </view>
      </view>
    </view>

    <!-- 处理弹窗 -->
    <view v-if="showHandleModal" class="modal-overlay" @click="closeHandle">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">处理告警</text>
          <text class="close-btn" @click="closeHandle">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">告警信息</text>
            <text class="form-text">{{ currentAlert?.message }}</text>
          </view>
          <view class="form-item">
            <text class="form-label">处理说明</text>
            <textarea
              v-model="handleRemark"
              class="form-textarea"
              placeholder="请输入处理说明..."
              maxlength="200"
            />
            <text class="form-count">{{ handleRemark.length }}/200</text>
          </view>
        </view>
        <view class="modal-footer">
          <button class="btn-cancel" @click="closeHandle">取消</button>
          <button class="btn-confirm" :loading="handleLoading" @click="submitHandle">确认处理</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAlertPage, handleAlert as handleAlertApi, type Alert } from '@/api/light'

const alertList = ref<Alert[]>([])
const loading = ref(false)
const currentLevel = ref<number | null>(null)
const currentType = ref<number | null>(null)
const currentStatus = ref(-1)
const pageNum = ref(1)
const pageSize = ref(20)
const totalCount = ref(0)
const unhandledCount = ref(0)
const handledCount = ref(0)
const typeIndex = ref(0)

const tabs = [
  { label: '全部', value: null },
  { label: '紧急', value: 4 },
  { label: '严重', value: 3 },
  { label: '一般', value: 2 },
  { label: '提示', value: 1 }
]

const typeOptions = ['全部类型', '过流', '过压', '欠压', '过热', '通讯故障', '其他']

const showHandleModal = ref(false)
const currentAlert = ref<Alert | null>(null)
const handleRemark = ref('')
const handleLoading = ref(false)

onMounted(() => {
  loadAlerts()
  loadStats()
})

async function loadAlerts() {
  loading.value = true
  try {
    const params: any = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (currentLevel.value !== null) params.alertLevel = currentLevel.value
    if (currentType.value !== null) params.alertType = currentType.value
    if (currentStatus.value >= 0) params.status = currentStatus.value
    const res = await getAlertPage(params)
    alertList.value = res.data?.records || []
    totalCount.value = res.data?.total || 0
  } catch (e: any) {
    console.error('加载告警失败', e)
    uni.showToast({ title: e.message || '加载告警失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getAlertPage({ pageNum: 1, pageSize: 1 })
    totalCount.value = res.data?.total || 0
    const unRes = await getAlertPage({ pageNum: 1, pageSize: 1, status: 0 })
    unhandledCount.value = unRes.data?.total || 0
    handledCount.value = totalCount.value - unhandledCount.value
  } catch (e: any) {
    console.error('加载告警统计失败', e)
  }
}

function filterByStatus(status: number) {
  currentStatus.value = status
  pageNum.value = 1
  loadAlerts()
}

function onLevelChange(level: number | null) {
  currentLevel.value = currentLevel.value === level ? null : level
  pageNum.value = 1
  loadAlerts()
}

function onTypeChange(e: { detail: { value: number } }) {
  typeIndex.value = e.detail.value
  currentType.value = e.detail.value === 0 ? null : e.detail.value
  pageNum.value = 1
  loadAlerts()
}

function getLevelClass(level: number): string {
  const map: Record<number, string> = {
    1: 'info',
    2: 'warning',
    3: 'critical',
    4: 'critical'
  }
  return map[level] || 'info'
}

function getLevelIcon(level: number): string {
  const map: Record<number, string> = {
    4: '🚨',
    3: '⚠',
    2: '⚡',
    1: 'ℹ'
  }
  return map[level] || 'ℹ'
}

function getTypeText(type: number): string {
  const map: Record<number, string> = {
    1: '过流',
    2: '过压',
    3: '欠压',
    4: '过热',
    5: '通讯故障',
    6: '其他'
  }
  return map[type] || '未知'
}

function formatTime(timeValue: any): string {
  if (!timeValue) return ''
  let date: Date
  try {
    if (Array.isArray(timeValue) && timeValue.length >= 5) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = timeValue
      date = new Date(year, month - 1, day, hour, minute, second)
    } else if (typeof timeValue === 'string') {
      date = new Date(timeValue.replace(' ', 'T'))
    } else {
      return String(timeValue)
    }
    if (isNaN(date.getTime())) return String(timeValue)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    if (diff < 0) return '刚刚'
    const minutes = Math.floor(diff / 60000)
    if (minutes < 1) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    const hours = Math.floor(diff / 3600000)
    if (hours < 24) return `${hours}小时前`
    return `${Math.floor(hours / 24)}天前`
  } catch (e) {
    return String(timeValue)
  }
}

function goDetail(alert: Alert) {
  uni.navigateTo({ url: `/pages/alerts/detail?id=${alert.id}` })
}

function handleAlert(alert: Alert) {
  currentAlert.value = alert
  handleRemark.value = ''
  showHandleModal.value = true
}

function closeHandle() {
  showHandleModal.value = false
  currentAlert.value = null
  handleRemark.value = ''
}

async function submitHandle() {
  if (!currentAlert.value) return
  if (!handleRemark.value.trim()) {
    uni.showToast({ title: '请输入处理说明', icon: 'none' })
    return
  }
  handleLoading.value = true
  try {
    const user = uni.getStorageSync('user')
    const handler = user ? JSON.parse(user).realName || JSON.parse(user).username : 'system'
    await handleAlertApi(currentAlert.value.id, {
      handler,
      handleRemark: handleRemark.value
    })
    uni.showToast({ title: '处理成功', icon: 'success' })
    closeHandle()
    loadAlerts()
    loadStats()
  } catch (e: any) {
    uni.showToast({ title: e.message || '处理失败', icon: 'none' })
  } finally {
    handleLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.alerts-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
}

.stats-row {
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  background: #fff;
}

.stat-card {
  flex: 1;
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 24rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 2rpx solid transparent;
  transition: all 0.2s;

  &.unhandled { background: rgba(245, 108, 108, 0.08); }
  &.handled { background: rgba(103, 194, 58, 0.08); }
  &.total { background: rgba(64, 158, 255, 0.08); }
}

.stat-num {
  font-size: 40rpx;
  font-weight: bold;
  margin-bottom: 4rpx;

  .unhandled & { color: #f56c6c; }
  .handled & { color: #67c23a; }
  .total & { color: #409eff; }
}

.stat-label {
  font-size: 24rpx;
  color: #909399;
}

.filter-section {
  background: #fff;
  padding: 0 24rpx 24rpx;
  border-top: 1rpx solid #f0f0f0;
}

.filter-tabs {
  display: flex;
  gap: 12rpx;
  padding: 16rpx 0;
}

.tab-item {
  padding: 10rpx 24rpx;
  background: #f5f7fa;
  border-radius: 24rpx;
  font-size: 26rpx;
  color: #606266;

  &.active {
    background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
    color: #fff;
  }
}

.search-wrap {
  display: flex;
  align-items: center;
  background: #f5f7fa;
  border-radius: 32rpx;
  padding: 0 20rpx;
  height: 72rpx;
}

.search-icon {
  font-size: 28rpx;
  margin-right: 12rpx;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
}

.clear-btn {
  font-size: 24rpx;
  color: #909399;
  padding: 8rpx;
}

.alert-list {
  padding: 24rpx;
}

.loading-tip, .empty-tip {
  padding: 80rpx 0;
  text-align: center;
  color: #909399;
  font-size: 28rpx;
}

.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 80rpx;
  margin-bottom: 16rpx;
  opacity: 0.4;
}

.alert-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
  border-left: 8rpx solid #909399;

  &.level-critical { border-left-color: #f56c6c; }
  &.level-warning { border-left-color: #e6a23c; }
  &.level-info { border-left-color: #409eff; }
}

.alert-header {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.level-badge {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  margin-right: 16rpx;

  &.critical { background: rgba(245, 108, 108, 0.15); color: #f56c6c; }
  &.warning { background: rgba(230, 162, 60, 0.15); color: #e6a23c; }
  &.info { background: rgba(64, 158, 255, 0.15); color: #409eff; }
}

.alert-title {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.title-text {
  font-size: 28rpx;
  color: #303133;
  font-weight: 500;
  margin-bottom: 4rpx;
}

.title-time {
  font-size: 22rpx;
  color: #909399;
}

.status-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;

  &.unhandled {
    background: #f56c6c;
    box-shadow: 0 0 8rpx #f56c6c;
  }
  &.handled {
    background: #67c23a;
  }
}

.alert-content {
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 16rpx;
}

.info-row {
  display: flex;
  font-size: 24rpx;
  line-height: 1.8;
}

.info-label {
  color: #909399;
  width: 100rpx;
}

.info-value {
  flex: 1;
  color: #606266;
}

.alert-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12rpx;
}

.status-text {
  font-size: 24rpx;
  color: #67c23a;
  margin-right: auto;
}

.action-btn {
  padding: 10rpx 24rpx;
  font-size: 24rpx;
  border-radius: 24rpx;
  background: #f5f7fa;
  color: #606266;

  &.primary {
    background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
    color: #fff;
  }
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  padding: 40rpx;
}

.modal-content {
  width: 100%;
  max-width: 600rpx;
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.modal-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #303133;
}

.close-btn {
  font-size: 36rpx;
  color: #909399;
  padding: 8rpx;
}

.modal-body {
  padding: 32rpx;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  display: block;
  font-size: 28rpx;
  color: #606266;
  margin-bottom: 12rpx;
}

.form-text {
  font-size: 26rpx;
  color: #303133;
  padding: 16rpx 20rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
}

.form-textarea {
  width: 100%;
  height: 200rpx;
  padding: 20rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.form-count {
  display: block;
  text-align: right;
  font-size: 22rpx;
  color: #909399;
  margin-top: 8rpx;
}

.modal-footer {
  display: flex;
  gap: 20rpx;
  padding: 24rpx 32rpx;
  border-top: 1rpx solid #f0f0f0;
}

.btn-cancel, .btn-confirm {
  flex: 1;
  height: 80rpx;
  border-radius: 40rpx;
  font-size: 28rpx;
  border: none;

  &::after {
    border: none;
  }
}

.btn-cancel {
  background: #f5f7fa;
  color: #606266;
}

.btn-confirm {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff;
}
</style>
