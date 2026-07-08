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
          <text class="form-label">触发照度阈值</text>
          <text class="form-value">{{ data.illuminanceThreshold }} lux</text>
        </view>
        <slider
          :value="data.illuminanceThreshold"
          :min="0"
          :max="500"
          :step="10"
          :show-value="false"
          activeColor="#409eff"
          backgroundColor="#ebeef5"
          block-color="#409eff"
          block-size="20"
          @change="e => data.illuminanceThreshold = e.detail.value"
        />
        <view class="range-tip">
          <text>0 lux</text>
          <text>500 lux</text>
        </view>
        <text class="form-tip">当环境照度低于该值时，自动开启路灯</text>
      </view>

      <view class="form-item">
        <view class="form-row-header">
          <text class="form-label">目标亮度</text>
          <text class="form-value">{{ data.targetBrightness }}%</text>
        </view>
        <slider
          :value="data.targetBrightness"
          :min="0"
          :max="100"
          :step="5"
          :show-value="false"
          activeColor="#67c23a"
          backgroundColor="#ebeef5"
          block-color="#67c23a"
          block-size="20"
          @change="e => data.targetBrightness = e.detail.value"
        />
        <view class="range-tip">
          <text>0%</text>
          <text>100%</text>
        </view>
        <text class="form-tip">触发后路灯的亮度值</text>
      </view>

      <view class="form-item">
        <view class="form-row-header">
          <text class="form-label">触发延迟</text>
          <text class="form-value">{{ data.triggerTime }} 秒</text>
        </view>
        <slider
          :value="data.triggerTime"
          :min="0"
          :max="300"
          :step="10"
          :show-value="false"
          activeColor="#e6a23c"
          backgroundColor="#ebeef5"
          block-color="#e6a23c"
          block-size="20"
          @change="e => data.triggerTime = e.detail.value"
        />
        <view class="range-tip">
          <text>0 秒</text>
          <text>300 秒</text>
        </view>
        <text class="form-tip">照度低于阈值持续该时间后才触发</text>
      </view>
    </view>

    <view class="preview-card">
      <text class="card-title">当前规则预览</text>
      <view class="preview-content">
        <text class="preview-text">
          当环境照度低于 <text class="highlight">{{ data.illuminanceThreshold }}</text> lux
          并持续 <text class="highlight">{{ data.triggerTime }}</text> 秒后，
          路灯自动调节到 <text class="highlight">{{ data.targetBrightness }}%</text> 亮度
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
import { getThreshold, updateThreshold, toggleThreshold, type ThresholdControl } from '@/api/light'

const data = reactive<ThresholdControl>({
  enabled: false,
  illuminanceThreshold: 100,
  targetBrightness: 80,
  triggerTime: 30
})

const saving = ref(false)
const toggling = ref(false)

onMounted(() => {
  loadData()
})

async function loadData() {
  try {
    const res = await getThreshold()
    if (res.data) Object.assign(data, res.data)
  } catch (e) {
    console.error('加载阈值配置失败', e)
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
