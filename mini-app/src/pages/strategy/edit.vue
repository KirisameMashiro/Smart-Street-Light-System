<template>
  <view class="edit-page">
    <view class="form-card">
      <view class="form-item">
        <text class="form-label required">策略名称</text>
        <input
          v-model="form.name"
          class="form-input"
          placeholder="请输入策略名称"
          maxlength="30"
        />
      </view>

      <view class="form-item">
        <text class="form-label required">策略类型</text>
        <view class="type-grid">
          <view
            v-for="opt in typeOptions"
            :key="opt.value"
            class="type-card"
            :class="{ active: form.type === opt.value }"
            @click="onTypeChange(opt.value)"
          >
            <text class="type-icon">{{ opt.icon }}</text>
            <text class="type-text">{{ opt.label }}</text>
          </view>
        </view>
      </view>

      <view v-if="form.type !== 'timed'" class="form-item">
        <text class="form-label required">适用星期</text>
        <view class="week-grid">
          <view
            v-for="(day, idx) in weekDays"
            :key="idx"
            class="week-item"
            :class="{ active: form.weekdays!.includes(idx + 1) }"
            @click="toggleWeekday(idx + 1)"
          >
            {{ day }}
          </view>
        </view>
      </view>

      <template v-if="form.type === 'timed'">
        <view class="form-item">
          <text class="form-label required">起始日期</text>
          <picker mode="date" :value="form.startDate" :min-date="minStartDate" @change="onStartDateChange">
            <view class="form-picker">
              <text :class="{ placeholder: !form.startDate }">
                {{ form.startDate || '请选择起始日期（明天开始）' }}
              </text>
              <text class="picker-arrow">›</text>
            </view>
          </picker>
        </view>
        <view class="form-item">
          <text class="form-label required">结束日期</text>
          <picker mode="date" :value="form.endDate" :min-date="form.startDate" @change="onEndDateChange">
            <view class="form-picker">
              <text :class="{ placeholder: !form.endDate }">
                {{ form.endDate || '请选择结束日期（大于起始日期）' }}
              </text>
              <text class="picker-arrow">›</text>
            </view>
          </picker>
        </view>
      </template>

      <view class="form-item">
        <text class="form-label required">适用时间</text>
        <view class="time-input-row">
          <input v-model="startHour" class="time-input" type="number" placeholder="hh" maxlength="2" />
          <text class="time-separator">:</text>
          <input v-model="startMinute" class="time-input" type="number" placeholder="mm" maxlength="2" />
          <text class="time-dash">—</text>
          <input v-model="endHour" class="time-input" type="number" placeholder="hh" maxlength="2" />
          <text class="time-separator">:</text>
          <input v-model="endMinute" class="time-input" type="number" placeholder="mm" maxlength="2" />
        </view>
        <text class="time-tip">小时: 0-23，分钟: 0-59</text>
      </view>

      <view class="form-item">
        <text class="form-label">亮度</text>
        <view class="brightness-wrap">
          <slider
            :value="form.brightness"
            :min="0"
            :max="100"
            :step="5"
            :show-value="true"
            activeColor="#409eff"
            backgroundColor="#ebeef5"
            block-color="#409eff"
            block-size="20"
            @change="e => form.brightness = e.detail.value"
          />
        </view>
      </view>

      <view class="form-item">
        <text class="form-label">行政区</text>
        <picker mode="selector" :range="districtOptions" @change="onDistrictChange">
          <view class="form-picker">
            <text :class="{ placeholder: !form.district }">
              {{ form.district || '请选择行政区' }}
            </text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="form-item">
        <text class="form-label">路段</text>
        <picker mode="selector" :range="filteredRoads" @change="onRoadChange">
          <view class="form-picker">
            <text :class="{ placeholder: !form.road }">
              {{ form.road || '请选择路段' }}
            </text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="form-item">
        <view class="switch-row">
          <text class="form-label">启用策略</text>
          <switch
            :checked="form.enabled"
            color="#67c23a"
            @change="e => form.enabled = e.detail.value"
          />
        </view>
      </view>
    </view>

    <view class="action-bar">
      <button class="btn-cancel" @click="goBack">取消</button>
      <button class="btn-confirm" :loading="loading" @click="handleSubmit">
        保存
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import {
  getDistricts,
  getRoads,
  getStrategyPage,
  addStrategy,
  updateStrategy,
  type TimedStrategy
} from '@/api/light'

const form = reactive<TimedStrategy>({
  name: '',
  type: 'daily',
  weekdays: [1, 2, 3, 4, 5],
  startTime: '',
  endTime: '',
  startDate: '',
  endDate: '',
  district: '',
  road: '',
  brightness: 80,
  enabled: true
})

const loading = ref(false)
const districtOptions = ref<string[]>([])
const roadOptions = ref<string[]>([])
const editingId = ref(0)

const startHour = ref('')
const startMinute = ref('')
const endHour = ref('')
const endMinute = ref('')

const districtRoadMap: Record<string, string[]> = {}
const roadDistrictMap: Record<string, string> = {}

const typeOptions = [
  { label: '工作日', value: 'workday', icon: '🏢' },
  { label: '每日', value: 'daily', icon: '📅' },
  { label: '节假日', value: 'holiday', icon: '🎉' },
  { label: '时间段', value: 'timed', icon: '⏳' }
]

const weekDays = ['一', '二', '三', '四', '五', '六', '日']

const minStartDate = computed(() => {
  const today = new Date()
  today.setDate(today.getDate() + 1)
  return today.toISOString().slice(0, 10)
})

const filteredRoads = computed(() => {
  if (!form.district) return roadOptions.value
  return districtRoadMap[form.district] || []
})

onMounted(() => {
  loadDistricts()
  loadRoads()
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  editingId.value = parseInt(page.options?.id) || 0
  if (editingId.value) {
    loadStrategy()
  }
})

async function loadDistricts() {
  try {
    const res = await getDistricts()
    districtOptions.value = res.data || []
  } catch (e) {
    console.error('加载行政区失败', e)
  }
}

async function loadRoads() {
  try {
    const res = await getRoads()
    roadOptions.value = res.data || []
  } catch (e) {
    console.error('加载路段失败', e)
  }
}

function onDistrictChange(e: { detail: { value: number } }) {
  const district = districtOptions.value[e.detail.value]
  form.district = district
  form.road = ''
}

function onRoadChange(e: { detail: { value: number } }) {
  const road = filteredRoads.value[e.detail.value]
  form.road = road
  if (road) {
    const district = roadDistrictMap[road]
    if (district) {
      form.district = district
    }
  }
}

async function loadStrategy() {
  try {
    const res = await getStrategyPage({ pageNum: 1, pageSize: 200 })
    const list: TimedStrategy[] = res.data?.records || []
    const found = list.find(s => s.id === editingId.value)
    if (found) {
      Object.assign(form, found)
      if (found.startTime) {
        const [sh, sm] = found.startTime.split(':')
        startHour.value = sh
        startMinute.value = sm
      }
      if (found.endTime) {
        const [eh, em] = found.endTime.split(':')
        endHour.value = eh
        endMinute.value = em
      }
    }
  } catch (e) {
    console.error('加载策略失败', e)
  }
}

function onTypeChange(type: string) {
  form.type = type
  if (type === 'workday') {
    form.weekdays = [1, 2, 3, 4, 5]
  } else if (type === 'daily') {
    form.weekdays = [1, 2, 3, 4, 5, 6, 7]
  } else if (type === 'holiday') {
    form.weekdays = [6, 7]
  } else if (type === 'timed') {
    form.weekdays = []
  }
}

function toggleWeekday(day: number) {
  if (!form.weekdays) form.weekdays = []
  const idx = form.weekdays.indexOf(day)
  if (idx >= 0) {
    form.weekdays.splice(idx, 1)
  } else {
    form.weekdays.push(day)
  }
}

function onStartDateChange(e: any) {
  form.startDate = e.detail.value
}

function onEndDateChange(e: any) {
  form.endDate = e.detail.value
}

function goBack() {
  uni.navigateBack()
}

async function handleSubmit() {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入策略名称', icon: 'none' })
    return
  }

  const sh = parseInt(startHour.value)
  const sm = parseInt(startMinute.value)
  const eh = parseInt(endHour.value)
  const em = parseInt(endMinute.value)

  if (isNaN(sh) || isNaN(sm) || isNaN(eh) || isNaN(em)) {
    uni.showToast({ title: '请完整填写时间', icon: 'none' })
    return
  }

  if (sh < 0 || sh > 23 || sm < 0 || sm > 59) {
    uni.showToast({ title: '开始时间无效，小时0-23，分钟0-59', icon: 'none' })
    return
  }

  if (eh < 0 || eh > 23 || em < 0 || em > 59) {
    uni.showToast({ title: '结束时间无效，小时0-23，分钟0-59', icon: 'none' })
    return
  }

  const pad = (n: number) => String(n).padStart(2, '0')
  form.startTime = `${pad(sh)}:${pad(sm)}`
  form.endTime = `${pad(eh)}:${pad(em)}`

  if (form.type === 'timed') {
    if (!form.startDate || !form.endDate) {
      uni.showToast({ title: '请选择日期范围', icon: 'none' })
      return
    }
    if (form.startDate > form.endDate) {
      uni.showToast({ title: '结束日期不能早于起始日期', icon: 'none' })
      return
    }
  } else {
    if (!form.weekdays || form.weekdays.length === 0) {
      uni.showToast({ title: '请选择适用星期', icon: 'none' })
      return
    }
  }

  loading.value = true
  try {
    if (editingId.value) {
      form.id = editingId.value
      await updateStrategy(form)
    } else {
      await addStrategy(form)
    }
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 800)
  } catch (e: any) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.edit-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 140rpx;
}

.form-card {
  margin: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.form-item {
  margin-bottom: 32rpx;
}

.form-item.half {
  flex: 1;
}

.form-row {
  display: flex;
  gap: 20rpx;
}

.form-label {
  display: block;
  font-size: 28rpx;
  color: #303133;
  margin-bottom: 16rpx;
  font-weight: 500;

  &.required::before {
    content: '*';
    color: #f56c6c;
    margin-right: 4rpx;
  }
}

.form-input {
  height: 88rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #303133;
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

.picker-arrow {
  color: #c0c4cc;
  font-size: 32rpx;
}

.placeholder {
  color: #c0c4cc;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.type-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #f5f7fa;
  border: 2rpx solid transparent;
  border-radius: 12rpx;
  padding: 24rpx 0;
  transition: all 0.2s;

  &.active {
    background: rgba(64, 158, 255, 0.08);
    border-color: #409eff;
  }
}

.type-icon {
  font-size: 40rpx;
  margin-bottom: 8rpx;
}

.type-text {
  font-size: 26rpx;
  color: #303133;
}

.week-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12rpx;
}

.week-item {
  height: 72rpx;
  background: #f5f7fa;
  border: 2rpx solid transparent;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: #606266;
  transition: all 0.2s;

  &.active {
    background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
    color: #fff;
  }
}

.time-input-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.time-input {
  width: 100rpx;
  height: 88rpx;
  padding: 0 16rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  font-size: 32rpx;
  text-align: center;
}

.time-separator {
  font-size: 32rpx;
  color: #409eff;
  font-weight: bold;
}

.time-dash {
  font-size: 32rpx;
  color: #909399;
  padding: 0 8rpx;
}

.time-tip {
  display: block;
  font-size: 24rpx;
  color: #909399;
  margin-top: 12rpx;
}

.brightness-wrap {
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 0 20rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.switch-row .form-label {
  margin-bottom: 0;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 20rpx;
  padding: 24rpx;
  background: #fff;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.btn-cancel, .btn-confirm {
  flex: 1;
  height: 88rpx;
  border-radius: 44rpx;
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
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff;
}
</style>