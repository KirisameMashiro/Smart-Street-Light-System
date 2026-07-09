<template>
  <view class="detail-page">
    <view v-if="loading" class="loading-overlay">
      <text>加载中...</text>
    </view>

    <view v-else class="content">
      <view class="header-card">
        <view class="light-icon" :class="getStatusClass(light.status)">
          <text>☀</text>
        </view>
        <view class="light-header-info">
          <text class="light-name">{{ light.lightName || light.lightCode }}</text>
          <text class="light-code">{{ light.lightCode }}</text>
          <view class="status-badge" :class="getStatusClass(light.status)">
            {{ getStatusText(light.status) }}
          </view>
        </view>
      </view>

      <view class="info-section">
        <view class="section-title">基本信息</view>
        <view class="info-list">
          <view class="info-item">
            <text class="info-label">设备类型</text>
            <text class="info-value">{{ light.deviceType || '-' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">额定功率</text>
            <text class="info-value">{{ light.ratedPower || '-' }} W</text>
          </view>
          <view class="info-item">
            <text class="info-label">行政区</text>
            <text class="info-value">{{ light.district || '-' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">路段</text>
            <text class="info-value">{{ light.road || '-' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">位置</text>
            <text class="info-value">{{ light.location || '-' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">坐标</text>
            <text class="info-value">{{ light.longitude?.toFixed(6) || '-' }}, {{ light.latitude?.toFixed(6) || '-' }}</text>
          </view>
        </view>
      </view>

      <view class="sensor-section">
        <view class="section-title">传感器数据</view>
        <view class="sensor-grid">
          <view class="sensor-card">
            <text class="sensor-icon">💡</text>
            <text class="sensor-value">{{ sensorData.illuminance || '-' }}</text>
            <text class="sensor-label">光照度 (lux)</text>
          </view>
          <view class="sensor-card">
            <text class="sensor-icon">⚡</text>
            <text class="sensor-value">{{ sensorData.power || '-' }}</text>
            <text class="sensor-label">功率 (W)</text>
          </view>
          <view class="sensor-card">
            <text class="sensor-icon">🔋</text>
            <text class="sensor-value">{{ sensorData.voltage || '-' }}</text>
            <text class="sensor-label">电压 (V)</text>
          </view>
          <view class="sensor-card">
            <text class="sensor-icon">〰</text>
            <text class="sensor-value">{{ sensorData.current || '-' }}</text>
            <text class="sensor-label">电流 (A)</text>
          </view>
          <view class="sensor-card">
            <text class="sensor-icon">🌡</text>
            <text class="sensor-value">{{ sensorData.temperature || '-' }}</text>
            <text class="sensor-label">温度 (°C)</text>
          </view>
          <view class="sensor-card">
            <text class="sensor-icon">💧</text>
            <text class="sensor-value">{{ sensorData.humidity || '-' }}</text>
            <text class="sensor-label">湿度 (%)</text>
          </view>
          <view class="sensor-card">
            <text class="sensor-icon">📊</text>
            <text class="sensor-value">{{ sensorData.samplingEnergy || '-' }}</text>
            <text class="sensor-label">采样耗电量 (kWh)</text>
          </view>
        </view>
      </view>

      <view class="control-section">
        <view class="section-title">远程控制</view>
        <view class="control-buttons">
          <button 
            class="control-btn" 
            :class="{ disabled: light.status === 3 }"
            :disabled="light.status === 3"
            @click="handleSwitch(1)"
          >
            <text class="btn-icon">▶</text>
            <text>开灯</text>
          </button>
          <button 
            class="control-btn off" 
            :class="{ disabled: light.status === 3 }"
            :disabled="light.status === 3"
            @click="handleSwitch(2)"
          >
            <text class="btn-icon">⏹</text>
            <text>关灯</text>
          </button>
        </view>
        
        <view class="brightness-control" :class="{ disabled: light.status === 3 }">
          <text class="brightness-label">亮度调节</text>
          <view class="brightness-slider-wrap">
            <slider 
              :value="brightness" 
              :min="0" 
              :max="100" 
              :step="5"
              activeColor="#409eff"
              backgroundColor="#ebeef5"
              block-size="28"
              :disabled="light.status === 3"
              @change="onBrightnessChange"
            />
            <text class="brightness-num">{{ brightness }}%</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getLightById, getLatestSensorData, switchLight, setLightBrightness, type Light, type SensorData } from '@/api/light'

const lightId = ref(0)
const loading = ref(true)
const brightness = ref(0)

const light = ref<Light>({
  id: 0,
  lightCode: '',
  lightName: '',
  location: '',
  longitude: 0,
  latitude: 0,
  status: 2,
  brightness: 0,
  deviceType: '',
  ratedPower: 0,
  district: '',
  road: ''
})

const sensorData = ref<SensorData>({
  illuminance: 0,
  power: 0,
  voltage: 0,
  current: 0,
  temperature: 0,
  humidity: 0,
  samplingEnergy: 0
})

onMounted(() => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  const options = (currentPage as any).$page?.options || {}
  lightId.value = parseInt(options.id) || 0
  
  if (lightId.value > 0) {
    fetchLightDetail()
    fetchSensorData()
  }
})

async function fetchLightDetail() {
  try {
    const res = await getLightById(lightId.value)
    light.value = res.data
    brightness.value = res.data.brightness || 0
  } catch (e) {
    console.error('获取路灯详情失败', e)
  } finally {
    loading.value = false
  }
}

async function fetchSensorData() {
  try {
    const res = await getLatestSensorData(lightId.value)
    sensorData.value = res.data
  } catch (e) {
    console.error('获取传感器数据失败', e)
  }
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

async function handleSwitch(status: number) {
  uni.showLoading({ title: '操作中...' })
  try {
    await switchLight(lightId.value, status)
    uni.showToast({ title: status === 1 ? '开灯成功' : '关灯成功', icon: 'success' })
    await fetchLightDetail()
  } catch (e: any) {
    uni.showToast({ title: e.message || '操作失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function onBrightnessChange(e: { detail: { value: number } }) {
  brightness.value = e.detail.value
  uni.showLoading({ title: '调节中...' })
  try {
    await setLightBrightness(lightId.value, brightness.value)
    uni.showToast({ title: '亮度调节成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '调节失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.loading-overlay text {
  font-size: 28rpx;
  color: #909399;
}

.content {
  padding-bottom: 32rpx;
}

.header-card {
  background: linear-gradient(135deg, #0a1628 0%, #1a2f4a 100%);
  padding: 48rpx 32rpx;
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.light-icon {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  background: rgba(103, 194, 58, 0.2);
  color: #67c23a;
  
  &.offline {
    background: rgba(144, 147, 153, 0.2);
    color: #909399;
  }
  
  &.fault {
    background: rgba(245, 108, 108, 0.2);
    color: #f56c6c;
  }
}

.light-name {
  font-size: 40rpx;
  font-weight: bold;
  color: #fff;
  display: block;
  margin-bottom: 8rpx;
}

.light-code {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.6);
  display: block;
  margin-bottom: 16rpx;
}

.status-badge {
  display: inline-block;
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
  font-size: 24rpx;
  background: rgba(103, 194, 58, 0.2);
  color: #67c23a;
  
  &.offline {
    background: rgba(144, 147, 153, 0.2);
    color: #909399;
  }
  
  &.fault {
    background: rgba(245, 108, 108, 0.2);
    color: #f56c6c;
  }
}

.info-section, .sensor-section, .control-section {
  margin: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #303133;
  margin-bottom: 24rpx;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.info-list {
  display: flex;
  flex-direction: column;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5f7fa;
  
  &:last-child {
    border-bottom: none;
  }
}

.info-label {
  font-size: 28rpx;
  color: #909399;
}

.info-value {
  font-size: 28rpx;
  color: #303133;
}

.sensor-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
}

.sensor-card {
  background: #f8f9fa;
  border-radius: 12rpx;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sensor-icon {
  font-size: 40rpx;
  margin-bottom: 12rpx;
}

.sensor-value {
  font-size: 36rpx;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8rpx;
}

.sensor-label {
  font-size: 22rpx;
  color: #909399;
}

.control-buttons {
  display: flex;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.control-btn {
  flex: 1;
  height: 96rpx;
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  color: #fff;
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  font-size: 30rpx;
  font-weight: bold;
  border: none;
  
  &::after {
    border: none;
  }
  
  &.off {
    background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
  }
  
  &.disabled {
    opacity: 0.5;
  }
}

.btn-icon {
  font-size: 28rpx;
}

.brightness-control {
  padding: 20rpx;
  background: #f8f9fa;
  border-radius: 12rpx;
  
  &.disabled {
    opacity: 0.5;
  }
}

.brightness-label {
  font-size: 28rpx;
  color: #606266;
  display: block;
  margin-bottom: 16rpx;
}

.brightness-slider-wrap {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.brightness-num {
  font-size: 28rpx;
  color: #409eff;
  font-weight: bold;
  width: 80rpx;
  text-align: right;
}
</style>