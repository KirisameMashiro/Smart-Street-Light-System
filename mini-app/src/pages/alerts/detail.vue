<template>
  <view class="detail-page">
    <view v-if="loading" class="loading">
      <text>加载中...</text>
    </view>
    <view v-else-if="!alert" class="empty">
      <text>告警信息不存在</text>
    </view>
    <view v-else class="content">
      <view class="level-card" :class="getLevelClass(alert.alertLevel)">
        <view class="level-icon">
          <text>{{ getLevelIcon(alert.alertLevel) }}</text>
        </view>
        <view class="level-info">
          <text class="level-text">{{ getLevelText(alert.alertLevel) }}</text>
          <text class="level-time">{{ formatFullTime(alert.createTime) }}</text>
        </view>
      </view>

      <view class="info-card">
        <text class="card-title">告警信息</text>
        <view class="info-list">
          <view class="info-row">
            <text class="info-label">告警内容</text>
            <text class="info-value">{{ alert.message }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">告警类型</text>
            <text class="info-value">{{ getTypeText(alert.alertType) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">告警等级</text>
            <text class="info-value" :class="getLevelClass(alert.alertLevel)">
              {{ getLevelText(alert.alertLevel) }}
            </text>
          </view>
          <view class="info-row">
            <text class="info-label">路灯ID</text>
            <text class="info-value link" @click="goLight">
              {{ alert.lightId || '-' }} ›
            </text>
          </view>
          <view class="info-row">
            <text class="info-label">处理状态</text>
            <text class="info-value">
              <text v-if="alert.status === 0" class="status-pending">未处理</text>
              <text v-else class="status-done">已处理</text>
            </text>
          </view>
        </view>
      </view>

      <view v-if="alert.handler" class="info-card">
        <text class="card-title">处理信息</text>
        <view class="info-list">
          <view class="info-row">
            <text class="info-label">处理人</text>
            <text class="info-value">{{ alert.handler }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">处理时间</text>
            <text class="info-value">{{ formatFullTime(alert.handleTime) }}</text>
          </view>
          <view class="info-row align-top">
            <text class="info-label">处理说明</text>
            <text class="info-value">{{ alert.handleRemark }}</text>
          </view>
        </view>
      </view>

      <view v-if="alert.status === 0" class="action-bar">
        <button class="action-btn primary" @click="showHandleModal = true">
          立即处理
        </button>
      </view>
    </view>

    <!-- 处理弹窗 -->
    <view v-if="showHandleModal" class="modal-overlay" @click="showHandleModal = false">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">处理告警</text>
          <text class="close-btn" @click="showHandleModal = false">✕</text>
        </view>
        <view class="modal-body">
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
          <button class="btn-cancel" @click="showHandleModal = false">取消</button>
          <button class="btn-confirm" :loading="handleLoading" @click="submitHandle">确认</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAlertDetail, handleAlert as handleAlertApi, type Alert } from '@/api/light'

const alert = ref<Alert | null>(null)
const loading = ref(true)
const showHandleModal = ref(false)
const handleRemark = ref('')
const handleLoading = ref(false)
const alertId = ref(0)

onMounted(() => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  alertId.value = parseInt(page.options?.id) || 0
  if (alertId.value) loadDetail()
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getAlertDetail(alertId.value)
    alert.value = res.data
  } catch (e) {
    console.error('加载告警详情失败', e)
  } finally {
    loading.value = false
  }
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

function getLevelText(level: number): string {
  const map: Record<number, string> = {
    4: '紧急告警',
    3: '严重告警',
    2: '一般告警',
    1: '提示信息'
  }
  return map[level] || '未知'
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

function formatFullTime(timeStr: string): string {
  if (!timeStr) return '-'
  try {
    const date = new Date(timeStr)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  } catch (e) {
    return timeStr
  }
}

function goLight() {
  if (alert.value?.lightId) {
    uni.navigateTo({ url: `/pages/detail/index?id=${alert.value.lightId}` })
  }
}

async function submitHandle() {
  if (!handleRemark.value.trim()) {
    uni.showToast({ title: '请输入处理说明', icon: 'none' })
    return
  }
  handleLoading.value = true
  try {
    const user = uni.getStorageSync('user')
    const handler = user ? JSON.parse(user).realName || JSON.parse(user).username : 'system'
    await handleAlertApi(alertId.value, {
      handler,
      handleRemark: handleRemark.value
    })
    uni.showToast({ title: '处理成功', icon: 'success' })
    showHandleModal.value = false
    loadDetail()
  } catch (e: any) {
    uni.showToast({ title: e.message || '处理失败', icon: 'none' })
  } finally {
    handleLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
}

.loading, .empty {
  padding: 200rpx 0;
  text-align: center;
  color: #909399;
  font-size: 28rpx;
}

.content {
  padding: 24rpx;
}

.level-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);

  &.critical { background: linear-gradient(135deg, #fef0f0 0%, #fde2e2 100%); }
  &.warning { background: linear-gradient(135deg, #fdf6ec 0%, #faecd8 100%); }
  &.info { background: linear-gradient(135deg, #ecf5ff 0%, #d9ecff 100%); }
}

.level-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  margin-right: 24rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);

  .critical & { color: #f56c6c; }
  .warning & { color: #e6a23c; }
  .info & { color: #409eff; }
}

.level-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.level-text {
  font-size: 36rpx;
  font-weight: bold;
  margin-bottom: 8rpx;

  .critical & { color: #f56c6c; }
  .warning & { color: #e6a23c; }
  .info & { color: #409eff; }
}

.level-time {
  font-size: 24rpx;
  color: #909399;
}

.info-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.card-title {
  display: block;
  font-size: 30rpx;
  font-weight: bold;
  color: #303133;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
  margin-bottom: 16rpx;
}

.info-list {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  padding: 16rpx 0;
  font-size: 28rpx;

  &.align-top {
    align-items: flex-start;
  }
}

.info-label {
  color: #909399;
  width: 160rpx;
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  color: #303133;
  text-align: right;

  &.link { color: #409eff; }
  &.critical { color: #f56c6c; }
  &.warning { color: #e6a23c; }
  &.info { color: #409eff; }

  .align-top & {
    text-align: left;
  }
}

.status-pending { color: #f56c6c; }
.status-done { color: #67c23a; }

.action-bar {
  padding: 24rpx;
}

.action-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 48rpx;
  font-size: 32rpx;
  font-weight: bold;
  border: none;

  &::after {
    border: none;
  }

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
