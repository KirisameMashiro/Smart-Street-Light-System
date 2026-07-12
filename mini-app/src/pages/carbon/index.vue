<template>
  <view class="carbon-page">
    <view class="header-bg"></view>

    <!-- 概览数据 -->
    <view class="summary-row">
      <view class="summary-card">
        <text class="card-icon">⚡</text>
        <text class="card-num">{{ summary.savedEnergy }}</text>
        <text class="card-unit">kWh</text>
        <text class="card-label">累计节电</text>
      </view>
      <view class="summary-card">
        <text class="card-icon">🌱</text>
        <text class="card-num">{{ summary.reducedCo2 }}</text>
        <text class="card-unit">kg</text>
        <text class="card-label">减排 CO₂</text>
      </view>
      <view class="summary-card">
        <text class="card-icon">📈</text>
        <text class="card-num">{{ summary.energySavingRate }}%</text>
        <text class="card-label">节能率</text>
      </view>
    </view>

    <!-- 时间筛选 -->
    <view class="filter-section">
      <view class="filter-tabs">
        <view
          v-for="t in timeTabs"
          :key="t.value"
          class="tab-item"
          :class="{ active: currentType === t.value }"
          @click="onTypeChange(t.value)"
        >
          {{ t.label }}
        </view>
      </view>
    </view>

    <!-- 趋势图 -->
    <view class="chart-card">
      <view class="chart-header">
        <text class="chart-title">节电趋势</text>
        <text class="chart-unit">单位：kWh</text>
      </view>
      <view class="chart-body">
        <view v-if="trendData.length === 0" class="empty-chart">
          <text>暂无数据</text>
        </view>
        <view v-else class="chart-area">
          <view
            v-for="(item, idx) in trendData"
            :key="idx"
            class="chart-bar-wrap"
          >
            <view
              class="chart-bar"
              :style="{ height: getBarHeight(item.savedEnergy) + '%' }"
            >
              <text class="bar-value">{{ item.savedEnergy }}</text>
            </view>
            <text class="bar-label">{{ formatDate(item.date) }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 路段对比 -->
    <view class="compare-card">
      <view class="card-header">
        <text class="card-title">路段节电对比</text>
        <text class="card-action" @click="loadRoadCompare">刷新</text>
      </view>
      <view v-if="roadData.length === 0" class="empty-chart">
        <text>暂无数据</text>
      </view>
      <view v-else class="road-list">
        <view
          v-for="(item, idx) in roadData"
          :key="idx"
          class="road-item"
        >
          <view class="road-info">
            <text class="road-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</text>
            <view class="road-text">
              <text class="road-name">{{ item.road }}</text>
              <text v-if="item.district" class="road-dist">{{ item.district }}</text>
            </view>
          </view>
          <view class="road-value-wrap">
            <view class="road-bar-track">
              <view
                class="road-bar-fill"
                :style="{ width: getRoadPercent(item.savedEnergy) + '%' }"
              ></view>
            </view>
            <text class="road-value">{{ item.savedEnergy }} kWh</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getCarbonSummary,
  getCarbonTrend,
  getRoadCompare,
  type CarbonSummary,
  type CarbonTrendItem,
  type RoadCompareItem
} from '@/api/light'

const summary = ref<CarbonSummary>({
  savedEnergy: 0,
  reducedCo2: 0,
  energySavingRate: 0
})

const trendData = ref<CarbonTrendItem[]>([])
const roadData = ref<RoadCompareItem[]>([])
const currentType = ref('month')
const maxBarValue = ref(0)

const timeTabs = [
  { label: '日度', value: 'day' },
  { label: '月度', value: 'month' },
  { label: '年度', value: 'year' }
]

onMounted(() => {
  loadSummary()
  loadTrend()
  loadRoadCompare()
})

async function loadSummary() {
  try {
    const period = getCurrentPeriod()
    const res = await getCarbonSummary(currentType.value, period)
    if (res.data) summary.value = res.data
  } catch (e) {
    console.error('加载概览数据失败', e)
    summary.value = { savedEnergy: 0, reducedCo2: 0, energySavingRate: 0 }
  }
}

async function loadTrend() {
  try {
    const period = getCurrentPeriod()
    const res = await getCarbonTrend(currentType.value, period)
    trendData.value = normalizeTrendData(res.data || [])
    maxBarValue.value = Math.max(...trendData.value.map(d => d.savedEnergy), 1)
  } catch (e) {
    console.error('加载趋势数据失败', e)
    trendData.value = []
    maxBarValue.value = 1
  }
}

async function loadRoadCompare() {
  try {
    const period = getCurrentPeriod()
    const res = await getRoadCompare(currentType.value, period)
    roadData.value = (res.data || []).sort((a, b) => b.savedEnergy - a.savedEnergy)
  } catch (e) {
    console.error('加载路段对比失败', e)
    roadData.value = []
  }
}

function getCurrentPeriod(): string {
  const now = new Date()
  if (currentType.value === 'day') {
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  } else if (currentType.value === 'month') {
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  } else {
    return `${now.getFullYear()}`
  }
}

function normalizeTrendData(data: any[]): CarbonTrendItem[] {
  if (!data || data.length === 0) return []
  return data.map(item => {
    // 兼容后端不同字段名: period/date/month/statDate
    const date = item.period || item.date || item.month || item.statDate || ''
    return {
      date: formatDate(date),
      savedEnergy: Number(item.savedEnergy || 0),
      reducedCo2: Number(item.reducedCo2 || item.co2Reduction || 0)
    }
  })
}

function onTypeChange(type: string) {
  currentType.value = type
  loadSummary()
  loadTrend()
  loadRoadCompare()
}

function getBarHeight(value: number): number {
  return Math.max(5, (value / maxBarValue.value) * 100)
}

function getRoadPercent(value: number): number {
  const max = Math.max(...roadData.value.map(r => r.savedEnergy), 1)
  return (value / max) * 100
}

function formatDate(date: string | number): string {
  if (!date) return '-'
  const s = String(date)
  // 月度每日趋势: 2025-07-01 -> 1日
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) {
    return parseInt(s.slice(8, 10)) + '日'
  }
  // 年度每月趋势: 1-12 -> X月
  if (/^\d{1,2}$/.test(s)) {
    return s + '月'
  }
  // 月度汇总: 2025-07 -> 7月
  if (/^\d{4}-\d{2}$/.test(s)) {
    return parseInt(s.slice(5, 7)) + '月'
  }
  return s
}
</script>

<style lang="scss" scoped>
.carbon-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
  position: relative;
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 400rpx;
  background: linear-gradient(135deg, #0a1628 0%, #1a2f4a 100%);
  z-index: 0;
}

.summary-row {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 16rpx;
  padding: 32rpx 24rpx;
}

.summary-card {
  flex: 1;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10rpx);
  border-radius: 16rpx;
  padding: 24rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 1rpx solid rgba(255, 255, 255, 0.15);
}

.card-icon {
  font-size: 40rpx;
  margin-bottom: 8rpx;
}

.card-num {
  font-size: 36rpx;
  font-weight: bold;
  color: #fff;
  margin-bottom: 4rpx;
}

.card-unit {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 8rpx;
}

.card-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.filter-section {
  position: relative;
  z-index: 1;
  padding: 0 24rpx 24rpx;
}

.filter-tabs {
  display: flex;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 32rpx;
  padding: 8rpx;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
  border-radius: 24rpx;
  transition: all 0.2s;

  &.active {
    background: #fff;
    color: #303133;
    font-weight: bold;
  }
}

.chart-card, .compare-card {
  position: relative;
  z-index: 1;
  background: #fff;
  border-radius: 16rpx;
  margin: 0 24rpx 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
  margin-bottom: 16rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: bold;
  color: #303133;
}

.card-action {
  font-size: 24rpx;
  color: #409eff;
  padding: 8rpx 16rpx;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
  margin-bottom: 16rpx;
}

.chart-title {
  font-size: 30rpx;
  font-weight: bold;
  color: #303133;
}

.chart-unit {
  font-size: 22rpx;
  color: #909399;
}

.chart-body {
  height: 360rpx;
  padding: 16rpx 0;
}

.empty-chart {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 28rpx;
}

.chart-area {
  height: 100%;
  display: flex;
  align-items: flex-end;
  gap: 8rpx;
}

.chart-bar-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.chart-bar {
  width: 80%;
  background: linear-gradient(180deg, #409eff 0%, #67c23a 100%);
  border-radius: 8rpx 8rpx 0 0;
  margin-top: auto;
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 8rpx;
  min-height: 20rpx;
  transition: height 0.3s;
}

.bar-value {
  position: absolute;
  top: -28rpx;
  font-size: 18rpx;
  color: #909399;
  white-space: nowrap;
}

.bar-label {
  font-size: 20rpx;
  color: #606266;
  margin-top: 8rpx;
  text-align: center;
}

.road-list {
  display: flex;
  flex-direction: column;
}

.road-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f7fa;

  &:last-child {
    border-bottom: none;
  }
}

.road-info {
  display: flex;
  align-items: center;
  width: 240rpx;
  margin-right: 20rpx;
}

.road-rank {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: bold;
  background: #ebeef5;
  color: #909399;
  margin-right: 12rpx;

  &.rank-1 { background: #f56c6c; color: #fff; }
  &.rank-2 { background: #e6a23c; color: #fff; }
  &.rank-3 { background: #67c23a; color: #fff; }
}

.road-text {
  display: flex;
  flex-direction: column;
}

.road-name {
  font-size: 28rpx;
  color: #303133;
  font-weight: 500;
}

.road-dist {
  font-size: 22rpx;
  color: #909399;
  margin-top: 2rpx;
}

.road-value-wrap {
  flex: 1;
  display: flex;
  align-items: center;
}

.road-bar-track {
  flex: 1;
  height: 16rpx;
  background: #f0f0f0;
  border-radius: 8rpx;
  overflow: hidden;
  margin-right: 16rpx;
}

.road-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  border-radius: 8rpx;
  transition: width 0.5s;
}

.road-value {
  font-size: 24rpx;
  color: #303133;
  font-weight: 500;
  min-width: 100rpx;
  text-align: right;
}
</style>
