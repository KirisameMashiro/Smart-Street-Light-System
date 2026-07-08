<template>
  <view class="control-page">
    <view class="control-tabs">
      <view 
        class="tab-item" 
        :class="{ active: currentTab === 'remote' }"
        @click="currentTab = 'remote'"
      >
        远程控制
      </view>
      <view 
        class="tab-item" 
        :class="{ active: currentTab === 'timed' }"
        @click="currentTab = 'timed'"
      >
        定时策略
      </view>
      <view 
        class="tab-item" 
        :class="{ active: currentTab === 'threshold' }"
        @click="currentTab = 'threshold'"
      >
        阈值联动
      </view>
    </view>

    <view v-if="currentTab === 'remote'" class="tab-content">
      <view class="search-bar">
        <view class="search-input-wrap">
          <text class="search-icon">🔍</text>
          <input 
            v-model="searchText" 
            class="search-input" 
            placeholder="搜索路灯编号"
          />
        </view>
      </view>

      <view class="filter-row">
        <picker :value="districtIndex" :range="districts" @change="onDistrictChange">
          <view class="filter-picker">
            {{ districts[districtIndex] || '选择行政区' }}
            <text class="arrow">▼</text>
          </view>
        </picker>
        <picker :value="roadIndex" :range="roads" @change="onRoadChange">
          <view class="filter-picker">
            {{ roads[roadIndex] || '选择路段' }}
            <text class="arrow">▼</text>
          </view>
        </picker>
      </view>

      <view class="batch-actions">
        <button class="batch-btn" @click="selectAll">
          {{ selectAllChecked ? '取消全选' : '全选' }}
        </button>
        <button class="batch-btn primary" @click="batchSwitch(1)">
          批量开灯
        </button>
        <button class="batch-btn danger" @click="batchSwitch(2)">
          批量关灯
        </button>
      </view>

      <view class="light-list">
        <view v-for="light in lights" :key="light.id" class="light-item">
          <view class="checkbox" :class="{ checked: selectedIds.includes(light.id) }" @click="toggleSelect(light.id)">
            <text v-if="selectedIds.includes(light.id)">✓</text>
          </view>
          <view class="light-info">
            <text class="light-name">{{ light.lightName || light.lightCode }}</text>
            <text class="light-location">{{ light.district }} · {{ light.road }}</text>
          </view>
          <view class="light-status" :class="getStatusClass(light.status)">
            {{ getStatusText(light.status) }}
          </view>
        </view>
      </view>
    </view>

    <view v-if="currentTab === 'timed'" class="tab-content">
      <view class="timed-list">
        <view v-for="strategy in timedStrategies" :key="strategy.id" class="strategy-item">
          <view class="strategy-header">
            <text class="strategy-name">{{ strategy.name }}</text>
            <view class="strategy-status" :class="{ active: strategy.enabled }">
              {{ strategy.enabled ? '启用' : '禁用' }}
            </view>
          </view>
          <view class="strategy-detail">
            <text>适用时间：{{ strategy.timeRange }}</text>
          </view>
          <view class="strategy-actions">
            <button class="action-btn" @click="editStrategy(strategy)">编辑</button>
            <button class="action-btn delete" @click="deleteStrategy(strategy.id)">删除</button>
          </view>
        </view>
      </view>
      <button class="add-btn" @click="showAddStrategy = true">+ 新增策略</button>
    </view>

    <view v-if="currentTab === 'threshold'" class="tab-content">
      <view class="threshold-list">
        <view v-for="rule in thresholdRules" :key="rule.id" class="rule-item">
          <view class="rule-header">
            <text class="rule-name">{{ rule.name }}</text>
            <view class="rule-status" :class="{ active: rule.enabled }">
              {{ rule.enabled ? '启用' : '禁用' }}
            </view>
          </view>
          <view class="rule-detail">
            <text>阈值条件：{{ rule.condition }}</text>
          </view>
        </view>
      </view>
      <button class="add-btn" @click="showAddRule = true">+ 新增规则</button>
    </view>

    <view v-if="showAddStrategy" class="modal-overlay" @click="showAddStrategy = false">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text>新增定时策略</text>
          <text class="close-btn" @click="showAddStrategy = false">×</text>
        </view>
        <view class="modal-body">
          <view class="form-item">
            <text class="form-label">策略名称</text>
            <input v-model="strategyForm.name" class="form-input" placeholder="请输入策略名称" />
          </view>
          <view class="form-item">
            <text class="form-label">策略类型</text>
            <picker :value="strategyTypeIndex" :range="strategyTypes" @change="onStrategyTypeChange">
              <view class="picker-value">
                {{ strategyTypes[strategyTypeIndex] }}
                <text class="arrow">▼</text>
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">适用时间</text>
            <input v-model="strategyForm.timeRange" class="form-input" placeholder="如：19:00-06:00" />
          </view>
          <view class="form-item">
            <text class="form-label">适用星期</text>
            <view class="week-select">
              <view 
                v-for="day in weekdays" 
                :key="day.value" 
                class="week-item"
                :class="{ selected: strategyForm.weekdays.includes(day.value) }"
                @click="toggleWeekday(day.value)"
              >
                {{ day.label }}
              </view>
            </view>
          </view>
          <view class="form-item">
            <text class="form-label">目标亮度</text>
            <input v-model="strategyForm.brightness" class="form-input" type="number" placeholder="0-100" />
          </view>
        </view>
        <view class="modal-footer">
          <button class="btn-cancel" @click="showAddStrategy = false">取消</button>
          <button class="btn-confirm" @click="saveStrategy">保存</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAllLights, getDistricts, getRoads, type Light } from '@/api/light'

const currentTab = ref('remote')
const searchText = ref('')
const loading = ref(false)

const districts = ref<string[]>([])
const roads = ref<string[]>([])
const districtIndex = ref(0)
const roadIndex = ref(0)

const lights = ref<Light[]>([])
const selectedIds = ref<number[]>([])

const strategyTypes = ['默认', '时间段']
const strategyTypeIndex = ref(0)
const weekdays = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 0 }
]

const showAddStrategy = ref(false)
const showAddRule = ref(false)

const strategyForm = ref({
  name: '',
  type: 'default',
  timeRange: '',
  weekdays: [] as number[],
  brightness: 50
})

const timedStrategies = ref([
  { id: 1, name: '夜间照明策略', timeRange: '19:00-06:00', enabled: true },
  { id: 2, name: '节能模式', timeRange: '00:00-05:00', enabled: false }
])

const thresholdRules = ref([
  { id: 1, name: '亮度自动调节', condition: '光照度 < 30lux', enabled: true },
  { id: 2, name: '温度保护', condition: '温度 > 60°C', enabled: false }
])

onMounted(() => {
  fetchLights()
  fetchOptions()
})

async function fetchLights() {
  try {
    const res = await getAllLights()
    lights.value = res.data
  } catch (e) {
    console.error('获取路灯数据失败', e)
  }
}

async function fetchOptions() {
  try {
    const [districtRes, roadRes] = await Promise.all([getDistricts(), getRoads()])
    districts.value = districtRes.data || []
    roads.value = roadRes.data || []
  } catch (e) {
    console.error('获取选项数据失败', e)
  }
}

function onDistrictChange(e: { detail: { value: number } }) {
  districtIndex.value = e.detail.value
}

function onRoadChange(e: { detail: { value: number } }) {
  roadIndex.value = e.detail.value
}

function getStatusClass(status: number) {
  const map: Record<number, string> = {
    1: 'online',
    2: 'offline',
    3: 'fault'
  }
  return map[status] || 'offline'
}

function getStatusText(status: number) {
  const map: Record<number, string> = {
    1: '在线',
    2: '离线',
    3: '故障'
  }
  return map[status] || '未知'
}

function toggleSelect(id: number) {
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

const selectAllChecked = ref(false)

function selectAll() {
  selectAllChecked.value = !selectAllChecked.value
  if (selectAllChecked.value) {
    selectedIds.value = lights.value.map(l => l.id)
  } else {
    selectedIds.value = []
  }
}

function batchSwitch(status: number) {
  if (selectedIds.value.length === 0) {
    uni.showToast({ title: '请选择路灯', icon: 'none' })
    return
  }
  uni.showToast({ title: `已${status === 1 ? '开灯' : '关灯'} ${selectedIds.value.length} 盏路灯`, icon: 'success' })
  selectedIds.value = []
  selectAllChecked.value = false
}

function onStrategyTypeChange(e: { detail: { value: number } }) {
  strategyTypeIndex.value = e.detail.value
  strategyForm.value.type = strategyTypes[e.detail.value] === '时间段' ? 'timed' : 'default'
}

function toggleWeekday(value: number) {
  const index = strategyForm.value.weekdays.indexOf(value)
  if (index > -1) {
    strategyForm.value.weekdays.splice(index, 1)
  } else {
    strategyForm.value.weekdays.push(value)
  }
}

function editStrategy(strategy: any) {
  uni.showToast({ title: '编辑功能开发中', icon: 'none' })
}

function deleteStrategy(id: number) {
  uni.showModal({
    title: '删除策略',
    content: '确定要删除该策略吗？',
    success: (res) => {
      if (res.confirm) {
        timedStrategies.value = timedStrategies.value.filter(s => s.id !== id)
        uni.showToast({ title: '删除成功', icon: 'success' })
      }
    }
  })
}

function saveStrategy() {
  if (!strategyForm.value.name || !strategyForm.value.timeRange) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }
  uni.showToast({ title: '保存成功', icon: 'success' })
  showAddStrategy.value = false
  strategyForm.value = {
    name: '',
    type: 'default',
    timeRange: '',
    weekdays: [],
    brightness: 50
  }
}
</script>

<style lang="scss" scoped>
.control-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 120rpx;
}

.control-tabs {
  display: flex;
  background: #fff;
  padding: 0 24rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.tab-item {
  flex: 1;
  padding: 32rpx 0;
  text-align: center;
  font-size: 30rpx;
  color: #606266;
  position: relative;
  
  &.active {
    color: #409eff;
    font-weight: bold;
    
    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 60rpx;
      height: 6rpx;
      background: #409eff;
      border-radius: 3rpx;
    }
  }
}

.tab-content {
  padding: 24rpx;
}

.search-bar {
  margin-bottom: 20rpx;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  background: #fff;
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

.filter-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.filter-picker {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #fff;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #303133;
}

.arrow {
  font-size: 20rpx;
  color: #909399;
}

.batch-actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.batch-btn {
  flex: 1;
  height: 72rpx;
  background: #fff;
  color: #606266;
  border-radius: 36rpx;
  font-size: 26rpx;
  border: 1rpx solid #dcdfe6;
  
  &::after {
    border: none;
  }
  
  &.primary {
    background: #409eff;
    color: #fff;
    border-color: #409eff;
  }
  
  &.danger {
    background: #f56c6c;
    color: #fff;
    border-color: #f56c6c;
  }
}

.light-list {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
}

.light-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:last-child {
    border-bottom: none;
  }
}

.checkbox {
  width: 44rpx;
  height: 44rpx;
  border: 2rpx solid #dcdfe6;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  font-size: 24rpx;
  color: #fff;
  
  &.checked {
    background: #409eff;
    border-color: #409eff;
  }
}

.light-info {
  flex: 1;
}

.light-name {
  font-size: 28rpx;
  color: #303133;
  display: block;
  margin-bottom: 8rpx;
}

.light-location {
  font-size: 24rpx;
  color: #909399;
}

.light-status {
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  font-size: 24rpx;
  background: rgba(144, 147, 153, 0.1);
  color: #909399;
  
  &.online {
    background: rgba(103, 194, 58, 0.1);
    color: #67c23a;
  }
  
  &.fault {
    background: rgba(245, 108, 108, 0.1);
    color: #f56c6c;
  }
}

.timed-list, .threshold-list {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
}

.strategy-item, .rule-item {
  padding: 24rpx;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:last-child {
    border-bottom: none;
  }
}

.strategy-header, .rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.strategy-name, .rule-name {
  font-size: 30rpx;
  font-weight: bold;
  color: #303133;
}

.strategy-status, .rule-status {
  padding: 6rpx 16rpx;
  border-radius: 16rpx;
  font-size: 22rpx;
  background: rgba(144, 147, 153, 0.1);
  color: #909399;
  
  &.active {
    background: rgba(103, 194, 58, 0.1);
    color: #67c23a;
  }
}

.strategy-detail, .rule-detail {
  font-size: 26rpx;
  color: #909399;
}

.strategy-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.action-btn {
  flex: 1;
  height: 64rpx;
  background: #f5f7fa;
  color: #606266;
  border-radius: 32rpx;
  font-size: 26rpx;
  border: none;
  
  &::after {
    border: none;
  }
  
  &.delete {
    background: rgba(245, 108, 108, 0.1);
    color: #f56c6c;
  }
}

.add-btn {
  width: 100%;
  height: 88rpx;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff;
  font-size: 32rpx;
  font-weight: bold;
  border-radius: 44rpx;
  margin-top: 24rpx;
  border: none;
  
  &::after {
    border: none;
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
  z-index: 1000;
}

.modal-content {
  width: 90%;
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
  font-size: 32rpx;
  font-weight: bold;
  color: #303133;
}

.close-btn {
  font-size: 48rpx;
  color: #909399;
  line-height: 1;
}

.modal-body {
  padding: 32rpx;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  font-size: 28rpx;
  color: #606266;
  display: block;
  margin-bottom: 12rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 20rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  font-size: 28rpx;
}

.picker-value {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 20rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  font-size: 28rpx;
}

.week-select {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.week-item {
  padding: 16rpx 24rpx;
  background: #f5f7fa;
  border-radius: 8rpx;
  font-size: 26rpx;
  color: #606266;
  
  &.selected {
    background: #409eff;
    color: #fff;
  }
}

.modal-footer {
  display: flex;
  padding: 24rpx 32rpx;
  gap: 24rpx;
  border-top: 1rpx solid #f0f0f0;
}

.btn-cancel, .btn-confirm {
  flex: 1;
  height: 80rpx;
  border-radius: 40rpx;
  font-size: 30rpx;
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
  background: #409eff;
  color: #fff;
}
</style>