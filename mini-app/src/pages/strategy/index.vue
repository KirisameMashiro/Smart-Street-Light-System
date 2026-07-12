<template>
  <view class="strategy-page">
    <view class="header-bar">
      <view class="search-wrap">
        <text class="search-icon">🔍</text>
        <input
          v-model="searchText"
          class="search-input"
          placeholder="搜索策略名称"
          @confirm="onSearch"
        />
        <text v-if="searchText" class="clear-btn" @click="clearSearch">✕</text>
      </view>
      <view class="filter-tabs">
        <view
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-item"
          :class="{ active: currentType === tab.value }"
          @click="onTypeChange(tab.value)"
        >
          {{ tab.label }}
        </view>
      </view>
    </view>

    <view class="strategy-list">
      <view v-if="loading && strategyList.length === 0" class="loading">
        <text>加载中...</text>
      </view>
      <view v-else-if="strategyList.length === 0" class="empty">
        <text class="empty-icon">📋</text>
        <text class="empty-text">暂无策略，点击下方按钮添加</text>
      </view>
      <view
        v-for="item in strategyList"
        :key="item.id"
        class="strategy-item"
        :class="{ disabled: !item.enabled }"
      >
        <view class="item-header">
          <view class="item-title">
            <text class="name-text">{{ item.name }}</text>
            <text class="type-tag" :class="item.type">
              {{ getTypeText(item.type) }}
            </text>
          </view>
          <switch
            :checked="item.enabled"
            :disabled="togglingId === item.id"
            color="#67c23a"
            @change="onToggle(item, $event)"
          />
        </view>
        <view class="item-content">
          <view class="info-row">
            <text class="info-icon">📅</text>
            <text class="info-text">
              <template v-if="item.type === 'timed'">
                {{ item.startDate }} ~ {{ item.endDate }}
              </template>
              <template v-else>
                <text v-for="day in item.weekdays" :key="day" class="week-tag">
                  每周{{ weekdayMap[day] }}
                </text>
              </template>
            </text>
          </view>
          <view class="info-row">
            <text class="info-icon">⏰</text>
            <text class="info-text">{{ item.startTime }} ~ {{ item.endTime }}</text>
          </view>
          <view class="info-row">
            <text class="info-icon">💡</text>
            <text class="info-text">亮度 {{ item.brightness }}%</text>
          </view>
          <view class="info-row">
            <text class="info-icon">📍</text>
            <text class="info-text">{{ formatArea(item) }}</text>
          </view>
        </view>
        <view class="item-footer">
          <view class="action-btn" @click="onEdit(item)">编辑</view>
          <view class="action-btn danger" @click="onDelete(item)">删除</view>
        </view>
      </view>
    </view>

    <view class="add-bar">
      <button class="add-btn" @click="onAdd">+ 新增策略</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getStrategyPage,
  deleteStrategy,
  toggleStrategy,
  type TimedStrategy
} from '@/api/light'

const strategyList = ref<TimedStrategy[]>([])
const loading = ref(false)
const searchText = ref('')
const currentType = ref('')
const togglingId = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const weekdayMap: Record<number, string> = {
  1: '一', 2: '二', 3: '三', 4: '四', 5: '五', 6: '六', 7: '日'
}

const tabs = [
  { label: '全部', value: '' },
  { label: '默认', value: 'default' },
  { label: '时间段', value: 'timed' }
]

onMounted(() => {
  loadStrategies()
})

async function loadStrategies() {
  loading.value = true
  try {
    const params: any = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (currentType.value) params.type = currentType.value
    if (searchText.value) params.name = searchText.value
    const res = await getStrategyPage(params)
    strategyList.value = res.data?.records || []
  } catch (e) {
    console.error('加载策略失败', e)
    strategyList.value = []
  } finally {
    loading.value = false
  }
}

function getTypeText(type: string): string {
  const map: Record<string, string> = {
    default: '默认',
    timed: '时间段'
  }
  return map[type] || type
}

function onTypeChange(type: string) {
  currentType.value = currentType.value === type ? '' : type
  pageNum.value = 1
  loadStrategies()
}

function onSearch() {
  pageNum.value = 1
  loadStrategies()
}

function clearSearch() {
  searchText.value = ''
  onSearch()
}

async function onToggle(item: TimedStrategy, e: any) {
  togglingId.value = item.id || 0
  try {
    await toggleStrategy(item.id!, e.detail.value)
    item.enabled = e.detail.value
    uni.showToast({ title: e.detail.value ? '已启用' : '已停用', icon: 'none' })
  } catch (err: any) {
    uni.showToast({ title: err.message || '操作失败', icon: 'none' })
    e.detail.value = !e.detail.value
  } finally {
    togglingId.value = 0
  }
}

function onAdd() {
  uni.navigateTo({ url: '/pages/strategy/edit' })
}

function onEdit(item: TimedStrategy) {
  uni.navigateTo({ url: `/pages/strategy/edit?id=${item.id}` })
}

function onDelete(item: TimedStrategy) {
  uni.showModal({
    title: '删除策略',
    content: `确定要删除策略"${item.name}"吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteStrategy(item.id!)
          uni.showToast({ title: '删除成功', icon: 'success' })
          loadStrategies()
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.strategy-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 140rpx;
}

.header-bar {
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

.filter-tabs {
  display: flex;
  gap: 12rpx;
  overflow-x: auto;
  white-space: nowrap;
}

.tab-item {
  padding: 10rpx 24rpx;
  background: #f5f7fa;
  border-radius: 24rpx;
  font-size: 26rpx;
  color: #606266;
  flex-shrink: 0;

  &.active {
    background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
    color: #fff;
  }
}

.strategy-list {
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

.strategy-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
  transition: all 0.2s;

  &.disabled {
    opacity: 0.6;
  }
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
  margin-bottom: 16rpx;
}

.item-title {
  display: flex;
  align-items: center;
  flex: 1;
}

.name-text {
  font-size: 30rpx;
  font-weight: bold;
  color: #303133;
  margin-right: 12rpx;
}

.type-tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 12rpx;

  &.default { background: rgba(64, 158, 255, 0.1); color: #409eff; }
  &.timed { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
}

.item-content {
  margin-bottom: 16rpx;
}

.info-row {
  display: flex;
  align-items: center;
  font-size: 26rpx;
  color: #606266;
  line-height: 2;
}

.info-icon {
  margin-right: 8rpx;
  font-size: 24rpx;
}

.info-text {
  flex: 1;
}

.week-tag {
  display: inline-block;
  padding: 2rpx 10rpx;
  background: #f5f7fa;
  border-radius: 8rpx;
  font-size: 22rpx;
  margin-right: 8rpx;
}

.item-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12rpx;
}

.action-btn {
  padding: 10rpx 24rpx;
  font-size: 24rpx;
  border-radius: 24rpx;
  background: #f5f7fa;
  color: #606266;

  &.danger {
    color: #f56c6c;
    background: rgba(245, 108, 108, 0.08);
  }
}

.add-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx;
  background: #fff;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.add-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff;
  font-size: 32rpx;
  font-weight: bold;
  border-radius: 48rpx;
  border: none;
  box-shadow: 0 8rpx 20rpx rgba(64, 158, 255, 0.3);

  &::after {
    border: none;
  }
}
</style>
