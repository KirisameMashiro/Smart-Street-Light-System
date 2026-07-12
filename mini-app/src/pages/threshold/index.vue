<template>
  <view class="threshold-page">
    <view class="header-card">
      <view class="header-icon">📊</view>
      <view class="header-text">
        <text class="header-title">智能阈值联动</text>
        <text class="header-desc">根据环境光照度自动调节路灯亮度</text>
      </view>
      <switch
        :checked="data.enabled"
        :disabled="toggling"
        color="#67c23a"
        @change="onToggle"
      />
    </view>

    <view class="form-card">
      <view class="form-item">
        <view class="form-row-header">
          <text class="form-label">关灯光照阈值</text>
          <text class="form-value">{{ data.lightOffThreshold }} lux</text>
        </view>
        <slider
          :value="data.lightOffThreshold"
          :min="0"
          :max="500"
          :step="10"
          :show-value="false"
          activeColor="#f56c6c"
          backgroundColor="#ebeef5"
          block-color="#f56c6c"
          block-size="20"
          @change="e => data.lightOffThreshold = e.detail.value"
        />
        <view class="range-tip">
          <text>0 lux</text>
          <text>500 lux</text>
        </view>
        <text class="form-tip">当环境照度高于该值时，自动关闭路灯</text>
      </view>

      <view class="divider"></view>

      <view class="section-title">
        <text class="section-title-text">开灯设置</text>
        <text class="add-btn" @click="addSegment">+ 添加档位</text>
      </view>

      <view v-for="(seg, idx) in data.segments" :key="idx" class="segment-card">
        <view class="segment-header">
          <view class="segment-index">{{ idx + 1 }}</view>
          <text class="segment-label">第 {{ idx + 1 }} 档</text>
          <text v-if="data.segments!.length > 1" class="delete-btn" @click="removeSegment(idx)">删除</text>
        </view>

        <view class="segment-item">
          <view class="form-row-header">
            <text class="form-label">光照阈值</text>
            <text class="form-value">{{ seg.threshold }} lux</text>
          </view>
          <slider
            :value="seg.threshold"
            :min="0"
            :max="500"
            :step="10"
            :show-value="false"
            activeColor="#409eff"
            backgroundColor="#ebeef5"
            block-color="#409eff"
            block-size="20"
            @change="e => seg.threshold = e.detail.value"
          />
          <text class="form-tip">低于此值开灯</text>
        </view>

        <view class="segment-item">
          <view class="form-row-header">
            <text class="form-label">亮度</text>
            <text class="form-value">{{ seg.brightness }}%</text>
          </view>
          <slider
            :value="seg.brightness"
            :min="0"
            :max="100"
            :step="5"
            :show-value="false"
            activeColor="#67c23a"
            backgroundColor="#ebeef5"
            block-color="#67c23a"
            block-size="20"
            @change="e => seg.brightness = e.detail.value"
          />
        </view>
      </view>

      </view>

    <view class="preview-card">
      <text class="card-title">当前规则预览</text>
      <view class="preview-content">
        <text class="preview-text">
          当光照高于 <text class="highlight">{{ data.lightOffThreshold }}</text> lux 时关灯；
          低于阈值时自动匹配最高档位亮度
        </text>
      </view>
    </view>

    <view class="action-bar">
      <button class="btn-confirm" :loading="saving" @click="handleSave">保存设置</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { getThreshold, updateThreshold, toggleThreshold, type ThresholdControl, type SegmentConfig } from '@/api/light'

const data = reactive<ThresholdControl>({
  enabled: false,
  lightOffThreshold: 100,
  segments: [
    { threshold: 30, brightness: 100 },
    { threshold: 60, brightness: 60 },
    { threshold: 90, brightness: 30 }
  ] as SegmentConfig[]
})

const saving = ref(false)
const toggling = ref(false)

onMounted(() => {
  loadData()
})

async function loadData() {
  try {
    const res = await getThreshold()
    if (res.data) {
      Object.assign(data, res.data)
      if (!data.segments || data.segments.length === 0) {
        data.segments = [
          { threshold: 30, brightness: 100 },
          { threshold: 60, brightness: 60 },
          { threshold: 90, brightness: 30 }
        ]
      }
    }
  } catch (e) {
    console.error('加载阈值配置失败', e)
  }
}

function addSegment() {
  if (!data.segments) data.segments = []
  const lastThreshold = data.segments.length > 0
    ? data.segments[data.segments.length - 1].threshold
    : 50
  data.segments.push({
    threshold: Math.min(lastThreshold + 20, 500),
    brightness: 50
  })
}

function removeSegment(idx: number) {
  if (data.segments && data.segments.length > 1) {
    data.segments.splice(idx, 1)
  }
}

async function onToggle(e: any) {
  toggling.value = true
  try {
    await toggleThreshold(e.detail.value)
    data.enabled = e.detail.value
    uni.showToast({ title: e.detail.value ? '已开启联动' : '已关闭联动', icon: 'none' })
  } catch (err: any) {
    uni.showToast({ title: err.message || '操作失败', icon: 'none' })
    e.detail.value = !e.detail.value
  } finally {
    toggling.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    if (data.segments) {
      data.segments.sort((a: SegmentConfig, b: SegmentConfig) => a.threshold - b.threshold)
    }
    await updateThreshold(data)
    uni.showToast({ title: '保存成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.threshold-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 140rpx;
}

.header-card {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #0a1628 0%, #1a2f4a 100%);
  margin: 24rpx;
  border-radius: 16rpx;
  padding: 32rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.15);
}

.header-icon {
  width: 80rpx;
  height: 80rpx;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  margin-right: 20rpx;
}

.header-text {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.header-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #fff;
  margin-bottom: 8rpx;
}

.header-desc {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.7);
}

.form-card {
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.form-item {
  margin-bottom: 40rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.form-row-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.form-label {
  font-size: 28rpx;
  color: #303133;
  font-weight: 500;
}

.form-value {
  font-size: 32rpx;
  font-weight: bold;
  color: #409eff;
}

.range-tip {
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
  color: #909399;
  margin-top: 4rpx;
  padding: 0 4rpx;
}

.form-tip {
  display: block;
  font-size: 22rpx;
  color: #909399;
  margin-top: 12rpx;
  padding: 0 4rpx;
}

.divider {
  height: 1rpx;
  background: #f0f0f0;
  margin: 32rpx 0;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.section-title-text {
  font-size: 30rpx;
  font-weight: bold;
  color: #303133;
}

.add-btn {
  font-size: 26rpx;
  color: #409eff;
}

.segment-card {
  background: #f9fafc;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid #ebeef5;
}

.segment-header {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
}

.segment-index {
  width: 48rpx;
  height: 48rpx;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: bold;
  margin-right: 16rpx;
}

.segment-label {
  flex: 1;
  font-size: 28rpx;
  color: #303133;
  font-weight: 500;
}

.delete-btn {
  font-size: 24rpx;
  color: #f56c6c;
}

.segment-item {
  margin-bottom: 24rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.preview-card {
  margin: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
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

.preview-content {
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9eb 100%);
  border-radius: 12rpx;
  padding: 24rpx;
}

.preview-text {
  font-size: 28rpx;
  color: #606266;
  line-height: 1.8;
}

.highlight {
  color: #409eff;
  font-weight: bold;
  padding: 0 4rpx;
  background: rgba(64, 158, 255, 0.1);
  border-radius: 6rpx;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx;
  background: #fff;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.btn-confirm {
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
