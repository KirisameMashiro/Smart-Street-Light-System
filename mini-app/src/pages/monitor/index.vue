<template>
  <view class="monitor-page">
    <view class="search-bar">
      <view class="search-input-wrap">
        <text class="search-icon">🔍</text>
        <input 
          v-model="searchText" 
          class="search-input" 
          placeholder="搜索路灯编号或名称"
          @confirm="handleSearch"
        />
      </view>
      <view class="filter-btn" @click="showFilter = !showFilter">
        <text>筛选</text>
      </view>
    </view>

    <view v-if="showFilter" class="filter-section">
      <view class="filter-item">
        <text class="filter-label">行政区</text>
        <picker :value="districtIndex" :range="districts" @change="onDistrictChange">
          <view class="picker-value">
            {{ districts[districtIndex] || '全部' }}
            <text class="arrow">▼</text>
          </view>
        </picker>
      </view>
      <view class="filter-item">
        <text class="filter-label">路段</text>
        <picker :value="roadIndex" :range="roads" @change="onRoadChange">
          <view class="picker-value">
            {{ roads[roadIndex] || '全部' }}
            <text class="arrow">▼</text>
          </view>
        </picker>
      </view>
      <view class="filter-item">
        <text class="filter-label">状态</text>
        <picker :value="statusIndex" :range="statusOptions" @change="onStatusChange">
          <view class="picker-value">
            {{ statusOptions[statusIndex] }}
            <text class="arrow">▼</text>
          </view>
        </picker>
      </view>
    </view>

    <view class="light-list">
      <view v-if="loading" class="loading-tip">
        <text>加载中...</text>
      </view>
      <view v-else-if="lights.length === 0" class="empty-tip">
        <text>暂无路灯数据</text>
      </view>
      <view 
        v-for="light in lights" 
        :key="light.id" 
        class="light-item"
        @click="goToDetail(light.id)"
      >
        <view class="light-status" :class="getStatusClass(light.status)">
          <text>{{ getStatusText(light.status) }}</text>
        </view>
        <view class="light-info">
          <text class="light-name">{{ light.lightName || light.lightCode }}</text>
          <text class="light-location">{{ light.district }} · {{ light.road }}</text>
        </view>
        <view class="light-brightness">
          <view class="brightness-bar">
            <view class="brightness-fill" :style="{ width: light.brightness + '%' }"></view>
          </view>
          <text class="brightness-value">{{ light.brightness }}%</text>
        </view>
      </view>
    </view>

    <view class="load-more" v-if="!loading && hasMore">
      <text @click="loadMore">加载更多</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getLightPage, getDistricts, getRoads, type Light } from '@/api/light'

const searchText = ref('')
const showFilter = ref(false)
const loading = ref(false)
const hasMore = ref(true)

const districts = ref<string[]>([])
const roads = ref<string[]>([])
const districtIndex = ref(0)
const roadIndex = ref(0)
const statusIndex = ref(0)
const statusOptions = ['全部', '在线', '离线', '故障']
const statusValues: (number | null)[] = [null, 1, 0, 2]

const lights = ref<Light[]>([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const selectedDistrict = computed(() => districts.value[districtIndex.value] || '')
const selectedRoad = computed(() => roads.value[roadIndex.value] || '')
const selectedStatus = computed(() => statusValues[statusIndex.value])

onMounted(() => {
  fetchData()
  fetchOptions()
})

async function fetchData(reset = true) {
  loading.value = true
  try {
    if (reset) {
      pageNum.value = 1
      lights.value = []
    }
    const params: any = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (searchText.value) params.keyword = searchText.value
    if (selectedDistrict.value) params.district = selectedDistrict.value
    if (selectedRoad.value) params.road = selectedRoad.value
    if (selectedStatus.value !== null) params.status = selectedStatus.value

    const res = await getLightPage(params)
    const records = res.data?.records || []
    if (reset) {
      lights.value = records
    } else {
      lights.value = lights.value.concat(records)
    }
    total.value = res.data?.total || 0
    hasMore.value = lights.value.length < total.value
  } catch (e) {
    console.error('获取路灯数据失败', e)
    lights.value = []
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

async function fetchOptions() {
  try {
    const [districtRes, roadRes] = await Promise.all([getDistricts(), getRoads()])
    districts.value = [''].concat(districtRes.data || [])
    roads.value = [''].concat(roadRes.data || [])
  } catch (e) {
    console.error('获取选项数据失败', e)
  }
}

function handleSearch() {
  fetchData()
}

function onDistrictChange(e: { detail: { value: number } }) {
  districtIndex.value = e.detail.value
  roadIndex.value = 0
  fetchData()
}

function onRoadChange(e: { detail: { value: number } }) {
  roadIndex.value = e.detail.value
  fetchData()
}

function onStatusChange(e: { detail: { value: number } }) {
  statusIndex.value = e.detail.value
  fetchData()
}

function getStatusClass(status: number) {
  const map: Record<number, string> = {
    1: 'online',
    0: 'offline',
    2: 'fault'
  }
  return map[status] || 'offline'
}

function getStatusText(status: number) {
  const map: Record<number, string> = {
    1: '在线',
    0: '离线',
    2: '故障'
  }
  return map[status] || '未知'
}

function goToDetail(id: number) {
  uni.navigateTo({ url: `/pages/detail/index?id=${id}` })
}

function loadMore() {
  if (hasMore.value && !loading.value) {
    pageNum.value++
    fetchData(false)
  } else if (!hasMore.value) {
    uni.showToast({ title: '已加载全部数据', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.monitor-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 120rpx;
}

.search-bar {
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  background: #fff;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  background: #f5f7fa;
  border-radius: 40rpx;
  padding: 0 24rpx;
}

.search-icon {
  font-size: 28rpx;
  margin-right: 12rpx;
}

.search-input {
  flex: 1;
  height: 72rpx;
  font-size: 28rpx;
}

.filter-btn {
  padding: 0 32rpx;
  height: 72rpx;
  background: #409eff;
  color: #fff;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}

.filter-section {
  background: #fff;
  padding: 24rpx;
  display: flex;
  gap: 24rpx;
  border-top: 1rpx solid #f0f0f0;
}

.filter-item {
  flex: 1;
}

.filter-label {
  font-size: 24rpx;
  color: #909399;
  display: block;
  margin-bottom: 12rpx;
}

.picker-value {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 20rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #303133;
}

.arrow {
  font-size: 20rpx;
  color: #909399;
}

.light-list {
  padding: 24rpx;
}

.loading-tip, .empty-tip {
  padding: 60rpx;
  text-align: center;
  color: #909399;
  font-size: 28rpx;
}

.light-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.light-status {
  display: inline-block;
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  font-size: 24rpx;
  margin-bottom: 16rpx;
  
  &.online {
    background: rgba(103, 194, 58, 0.1);
    color: #67c23a;
  }
  
  &.offline {
    background: rgba(144, 147, 153, 0.1);
    color: #909399;
  }
  
  &.fault {
    background: rgba(245, 108, 108, 0.1);
    color: #f56c6c;
  }
}

.light-name {
  font-size: 32rpx;
  font-weight: bold;
  color: #303133;
  display: block;
  margin-bottom: 8rpx;
}

.light-location {
  font-size: 26rpx;
  color: #909399;
  display: block;
  margin-bottom: 20rpx;
}

.light-brightness {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.brightness-bar {
  flex: 1;
  height: 12rpx;
  background: #ebeef5;
  border-radius: 6rpx;
  overflow: hidden;
}

.brightness-fill {
  height: 100%;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  border-radius: 6rpx;
}

.brightness-value {
  font-size: 26rpx;
  color: #606266;
  width: 80rpx;
  text-align: right;
}

.load-more {
  padding: 32rpx;
  text-align: center;
  color: #409eff;
  font-size: 28rpx;
}
</style>