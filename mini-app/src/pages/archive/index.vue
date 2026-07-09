<template>
  <view class="archive-page">
    <view class="search-bar">
      <view class="search-input-wrap">
        <text class="search-icon">🔍</text>
        <input
          v-model="searchKeyword"
          class="search-input"
          placeholder="搜索编号/名称/位置"
          @input="onSearch"
        />
        <text v-if="searchKeyword" class="clear-icon" @click="clearSearch">✕</text>
      </view>
    </view>

    <view class="filter-row">
      <picker :value="districtIndex" :range="districtOptions" @change="onDistrictChange">
        <view class="filter-picker">
          {{ districtOptions[districtIndex] || '行政区' }}
          <text class="arrow">▼</text>
        </view>
      </picker>
      <picker :value="roadIndex" :range="filteredRoads" @change="onRoadChange">
        <view class="filter-picker">
          {{ filteredRoads[roadIndex] || '路段' }}
          <text class="arrow">▼</text>
        </view>
      </picker>
      <picker :value="statusIndex" :range="statusOptions" @change="onStatusChange">
        <view class="filter-picker">
          {{ statusOptions[statusIndex] || '状态' }}
          <text class="arrow">▼</text>
        </view>
      </picker>
    </view>

    <view class="light-list">
      <view v-if="lightList.length === 0 && !loading" class="empty-tip">
        <text class="empty-icon">💡</text>
        <text class="empty-text">暂无路灯数据</text>
      </view>
      <view
        v-for="light in lightList"
        :key="light.id"
        class="light-card"
        @click="goDetail(light.id)"
      >
        <view class="light-header">
          <text class="light-name">{{ light.lightName || light.lightCode }}</text>
          <view class="light-status" :class="getStatusClass(light.status)">
            <view class="status-dot"></view>
            {{ getStatusText(light.status) }}
          </view>
        </view>
        <view class="light-info">
          <text class="info-label">编号：</text>
          <text class="info-value">{{ light.lightCode }}</text>
        </view>
        <view class="light-info">
          <text class="info-label">位置：</text>
          <text class="info-value">{{ light.location }}</text>
        </view>
        <view class="light-info">
          <text class="info-label">行政区：</text>
          <text class="info-value">{{ light.district }}</text>
        </view>
        <view class="light-info">
          <text class="info-label">路段：</text>
          <text class="info-value">{{ light.road }}</text>
        </view>
        <view class="light-info">
          <text class="info-label">类型：</text>
          <text class="info-value">{{ light.deviceType }}</text>
        </view>
        <view class="light-info" v-if="light.brightness !== undefined">
          <text class="info-label">亮度：</text>
          <text class="info-value">{{ light.brightness }}%</text>
        </view>
      </view>
    </view>

    <view v-if="loading" class="loading-tip">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getLightPage, getAllLights, getDistricts, getRoads, type Light } from '@/api/light'

const searchKeyword = ref('')
const loading = ref(false)
const lightList = ref<Light[]>([])

const districtOptions = ref<string[]>([])
const roadOptions = ref<string[]>([])
const districtIndex = ref(0)
const roadIndex = ref(0)
const statusIndex = ref(0)
const statusOptions = ['全部', '在线', '离线', '故障']

const districtRoadMap: Record<string, string[]> = {}
const roadDistrictMap: Record<string, string> = {}

const selectedDistrict = computed(() => districtOptions.value[districtIndex.value] || '')
const selectedRoad = computed(() => filteredRoads.value[roadIndex.value] || '')
const selectedStatus = computed(() => {
  const map: Record<number, number | null> = { 0: null, 1: 1, 2: 0, 3: 2 }
  return map[statusIndex.value] ?? null
})

const filteredRoads = computed(() => {
  if (!selectedDistrict.value) return roadOptions.value
  return districtRoadMap[selectedDistrict.value] || []
})

onMounted(() => {
  loadData()
  loadOptions()
})

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      pageNum: 1,
      pageSize: 50
    }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (selectedDistrict.value) params.district = selectedDistrict.value
    if (selectedRoad.value) params.road = selectedRoad.value
    if (selectedStatus.value !== null) params.status = selectedStatus.value
    
    const res = await getLightPage(params)
    lightList.value = res.data?.records || res.data || []
  } catch (e) {
    console.error('加载路灯列表失败', e)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [dRes, rRes, lightsRes] = await Promise.all([
      getDistricts(),
      getRoads(),
      getAllLights()
    ])
    districtOptions.value = [''].concat(dRes.data || [])
    roadOptions.value = [''].concat(rRes.data || [])
    
    const lights = lightsRes.data || []
    lights.forEach(light => {
      if (light.district && light.road) {
        if (!districtRoadMap[light.district]) {
          districtRoadMap[light.district] = []
        }
        if (!districtRoadMap[light.district].includes(light.road)) {
          districtRoadMap[light.district].push(light.road)
        }
        if (!roadDistrictMap[light.road]) {
          roadDistrictMap[light.road] = light.district
        }
      }
    })
  } catch (e) {
    console.error('加载选项失败', e)
  }
}

function onSearch() {
  loadData()
}

function clearSearch() {
  searchKeyword.value = ''
  loadData()
}

function onDistrictChange(e: { detail: { value: number } }) {
  districtIndex.value = e.detail.value
  roadIndex.value = 0
  loadData()
}

function onRoadChange(e: { detail: { value: number } }) {
  roadIndex.value = e.detail.value
  const road = filteredRoads.value[e.detail.value]
  if (road && road !== '') {
    const district = roadDistrictMap[road]
    const idx = districtOptions.value.indexOf(district)
    if (idx >= 0) {
      districtIndex.value = idx
    }
  }
  loadData()
}

function onStatusChange(e: { detail: { value: number } }) {
  statusIndex.value = e.detail.value
  loadData()
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/detail/index?id=${id}` })
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
</script>

<style lang="scss" scoped>
.archive-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
}

.search-bar {
  padding: 20rpx 24rpx;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 40rpx;
  padding: 0 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
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

.clear-icon {
  font-size: 24rpx;
  color: #909399;
  padding: 8rpx;
}

.filter-row {
  display: flex;
  gap: 16rpx;
  padding: 0 24rpx 20rpx;
}

.filter-picker {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 20rpx;
  background: #fff;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: #303133;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.arrow {
  font-size: 20rpx;
  color: #909399;
}

.light-list {
  padding: 0 24rpx;
}

.empty-tip {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 100rpx;
  opacity: 0.4;
  margin-bottom: 20rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #909399;
}

.light-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.light-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.light-name {
  font-size: 32rpx;
  font-weight: bold;
  color: #303133;
}

.light-status {
  display: flex;
  align-items: center;
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  
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

.status-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: currentColor;
  margin-right: 8rpx;
}

.light-info {
  display: flex;
  font-size: 26rpx;
  line-height: 1.8;
}

.info-label {
  color: #909399;
  min-width: 120rpx;
}

.info-value {
  color: #303133;
  flex: 1;
}

.loading-tip {
  text-align: center;
  padding: 40rpx;
  color: #909399;
  font-size: 26rpx;
}
</style>
