<template>
  <view class="logs-page">
    <view class="filter-bar">
      <view class="search-wrap">
        <text class="search-icon">🔍</text>
        <input
          v-model="searchText"
          class="search-input"
          placeholder="搜索操作人/内容"
          @confirm="onSearch"
        />
        <text v-if="searchText" class="clear-btn" @click="clearSearch">✕</text>
      </view>
      <view class="filter-row">
        <picker mode="selector" :range="typeOptions" @change="onTypeChange">
          <view class="filter-picker">
            <text>{{ currentTypeText }}</text>
            <text class="picker-arrow">▾</text>
          </view>
        </picker>
        <view class="filter-picker" @click="showDatePicker = true">
          <text>{{ currentDateText }}</text>
          <text class="picker-arrow">📅</text>
        </view>
      </view>
    </view>

    <view class="log-list">
      <view v-if="loading && logList.length === 0" class="loading">
        <text>加载中...</text>
      </view>
      <view v-else-if="logList.length === 0" class="empty">
        <text class="empty-icon">📋</text>
        <text class="empty-text">暂无日志记录</text>
      </view>
      <view
        v-for="log in logList"
        :key="log.id"
        class="log-item"
      >
        <view class="log-dot" :class="getTypeClass(log.type)"></view>
        <view class="log-content">
          <view class="log-header">
            <text class="log-operator">{{ log.operatorName || log.operator }}</text>
            <text class="log-type" :class="getTypeClass(log.type)">
              {{ log.type }}
            </text>
          </view>
          <view class="log-detail">
            <text class="detail-label">内容：</text>
            <text class="detail-value">{{ log.content }}</text>
          </view>
          <view class="log-detail">
            <text class="detail-label">结果：</text>
            <text class="detail-value">{{ log.result }}</text>
          </view>
          <view class="log-footer">
            <text class="log-time">{{ formatFullTime(log.createTime) }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="logList.length > 0" class="load-more" @click="loadMore">
      <text>{{ loadingMore ? '加载中...' : '加载更多' }}</text>
    </view>

    <!-- 日期选择弹窗 -->
    <view v-if="showDatePicker" class="modal-overlay" @click="showDatePicker = false">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">选择日期范围</text>
          <text class="close-btn" @click="showDatePicker = false">✕</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">开始日期</text>
            <picker mode="date" :value="dateRange.start" @change="e => dateRange.start = e.detail.value">
              <view class="form-picker">
                <text :class="{ placeholder: !dateRange.start }">
                  {{ dateRange.start || '请选择' }}
                </text>
                <text class="picker-arrow">›</text>
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">结束日期</text>
            <picker mode="date" :value="dateRange.end" @change="e => dateRange.end = e.detail.value">
              <view class="form-picker">
                <text :class="{ placeholder: !dateRange.end }">
                  {{ dateRange.end || '请选择' }}
                </text>
                <text class="picker-arrow">›</text>
              </view>
            </picker>
          </view>
        </view>
        <view class="modal-footer">
          <button class="btn-cancel" @click="resetDate">重置</button>
          <button class="btn-confirm" @click="confirmDate">确定</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getOperationLogs, type OperationLog } from '@/api/light'

const logList = ref<OperationLog[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const searchText = ref('')
const currentType = ref('')
const showDatePicker = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const dateRange = ref({
  start: '',
  end: ''
})

const typeOptions = ['全部类型']

const currentTypeText = computed(() => {
  return currentType.value || '全部类型'
})

const currentDateText = computed(() => {
  if (!dateRange.value.start && !dateRange.value.end) return '选择日期'
  return `${dateRange.value.start || '开始'} ~ ${dateRange.value.end || '现在'}`
})

onMounted(() => {
  loadLogs()
})

async function loadLogs() {
  loading.value = true
  try {
    const params: any = {
      pageNum: 1,
      pageSize: pageSize.value
    }
    if (searchText.value) params.operator = searchText.value
    if (currentType.value) params.type = currentType.value
    if (dateRange.value.start) params.startTime = dateRange.value.start
    if (dateRange.value.end) params.endTime = dateRange.value.end + ' 23:59:59'

    const res = await getOperationLogs(params)
    logList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('加载日志失败', e)
    logList.value = []
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || logList.value.length >= total.value) return
  loadingMore.value = true
  try {
    pageNum.value++
    const params: any = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (searchText.value) params.operator = searchText.value
    if (currentType.value) params.type = currentType.value
    if (dateRange.value.start) params.startTime = dateRange.value.start
    if (dateRange.value.end) params.endTime = dateRange.value.end + ' 23:59:59'

    const res = await getOperationLogs(params)
    logList.value = logList.value.concat(res.data?.records || [])
  } catch (e) {
    console.error('加载更多失败', e)
  } finally {
    loadingMore.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadLogs()
}

function clearSearch() {
  searchText.value = ''
  onSearch()
}

function onTypeChange(e: any) {
  // 后端类型不做具体枚举，简化处理
  pageNum.value = 1
  loadLogs()
}

function confirmDate() {
  showDatePicker.value = false
  pageNum.value = 1
  loadLogs()
}

function resetDate() {
  dateRange.value = { start: '', end: '' }
  showDatePicker.value = false
  pageNum.value = 1
  loadLogs()
}

function getTypeClass(type: string): string {
  return 'other'
}

function formatFullTime(timeStr: string): string {
  if (!timeStr) return ''
  try {
    const date = new Date(timeStr)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  } catch (e) {
    return timeStr
  }
}
</script>

<style lang="scss" scoped>
.logs-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
}

.filter-bar {
  background: #fff;
  padding: 24rpx;
}

.search-wrap {
  display: flex;
  align-items: center;
  background: #f5f7fa;
  border-radius: 32rpx;
  padding: 0 20rpx;
  height: 72rpx;
  margin-bottom: 16rpx;
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

.filter-row {
  display: flex;
  gap: 16rpx;
}

.filter-picker {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 0 20rpx;
  height: 72rpx;
  font-size: 26rpx;
  color: #303133;
}

.picker-arrow {
  color: #909399;
  font-size: 24rpx;
}

.log-list {
  padding: 24rpx;
}

.loading, .empty {
  padding: 80rpx 0;
  text-align: center;
  color: #909399;
  font-size: 28rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 80rpx;
  margin-bottom: 16rpx;
  opacity: 0.4;
}

.log-item {
  display: flex;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
  position: relative;
}

.log-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  margin-top: 8rpx;
  margin-right: 20rpx;
  flex-shrink: 0;

  &.login { background: #409eff; }
  &.operate { background: #67c23a; }
  &.alert { background: #f56c6c; }
  &.strategy { background: #e6a23c; }
  &.other { background: #909399; }
}

.log-content {
  flex: 1;
}

.log-header {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}

.log-operator {
  font-size: 28rpx;
  font-weight: bold;
  color: #303133;
  margin-right: 12rpx;
}

.log-type {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 12rpx;

  &.login { background: rgba(64, 158, 255, 0.1); color: #409eff; }
  &.operate { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
  &.alert { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
  &.strategy { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
  &.other { background: rgba(144, 147, 153, 0.1); color: #909399; }
}

.log-detail {
  display: flex;
  font-size: 24rpx;
  line-height: 1.8;
}

.detail-label {
  color: #909399;
  width: 80rpx;
  flex-shrink: 0;
}

.detail-value {
  flex: 1;
  color: #606266;
}

.log-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #909399;
}

.load-more {
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: #409eff;
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

.form-picker {
  height: 88rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 0 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 28rpx;
  color: #303133;
}

.placeholder {
  color: #c0c4cc;
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
