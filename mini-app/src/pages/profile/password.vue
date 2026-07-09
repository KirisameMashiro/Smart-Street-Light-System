<template>
  <view class="password-page">
    <view class="header-tip">
      <text class="tip-text">定期修改密码可提高账号安全性</text>
    </view>

    <view class="form-card">
      <view class="form-item">
        <view class="input-wrap" :class="{ focus: oldFocus }">
          <text class="input-icon">🔒</text>
          <input
            v-model="form.oldPassword"
            class="input"
            :type="showOld ? 'text' : 'password'"
            placeholder="请输入旧密码"
            placeholder-class="placeholder"
            @focus="oldFocus = true"
            @blur="oldFocus = false"
          />
          <text class="toggle-eye" @click="showOld = !showOld">
            {{ showOld ? '👁' : '👁‍🗨' }}
          </text>
        </view>
      </view>

      <view class="form-item">
        <view class="input-wrap" :class="{ focus: newFocus }">
          <text class="input-icon">🔑</text>
          <input
            v-model="form.newPassword"
            class="input"
            :type="showNew ? 'text' : 'password'"
            placeholder="请输入新密码（6-20位）"
            placeholder-class="placeholder"
            @focus="newFocus = true"
            @blur="newFocus = false"
          />
          <text class="toggle-eye" @click="showNew = !showNew">
            {{ showNew ? '👁' : '👁‍🗨' }}
          </text>
        </view>
        <view v-if="form.newPassword" class="strength-bar">
          <view class="strength-track">
            <view
              class="strength-fill"
              :class="strengthClass"
              :style="{ width: strengthPercent }"
            ></view>
          </view>
          <text class="strength-text" :class="strengthClass">{{ strengthText }}</text>
        </view>
      </view>

      <view class="form-item">
        <view class="input-wrap" :class="{ focus: confirmFocus }">
          <text class="input-icon">✅</text>
          <input
            v-model="form.confirmPassword"
            class="input"
            :type="showConfirm ? 'text' : 'password'"
            placeholder="请再次输入新密码"
            placeholder-class="placeholder"
            @focus="confirmFocus = true"
            @blur="confirmFocus = false"
            @confirm="handleSubmit"
          />
          <text class="toggle-eye" @click="showConfirm = !showConfirm">
            {{ showConfirm ? '👁' : '👁‍🗨' }}
          </text>
        </view>
      </view>

      <button
        class="submit-btn"
        :class="{ active: canSubmit }"
        :loading="loading"
        :disabled="loading || !canSubmit"
        @click="handleSubmit"
      >
        确认修改
      </button>
    </view>

    <view class="tips-card">
      <text class="tips-title">密码安全提示</text>
      <view class="tips-list">
        <text class="tips-item">• 密码长度建议 6-20 位</text>
        <text class="tips-item">• 建议包含字母、数字和特殊字符</text>
        <text class="tips-item">• 避免使用连续或重复的字符</text>
        <text class="tips-item">• 不要使用与其他账号相同的密码</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { changePassword } from '@/api/light'

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
const showOld = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)
const oldFocus = ref(false)
const newFocus = ref(false)
const confirmFocus = ref(false)

const canSubmit = computed(() => {
  return form.oldPassword && form.newPassword && form.confirmPassword
})

const strengthLevel = computed(() => {
  const pwd = form.newPassword
  if (!pwd) return 0
  let level = 0
  if (pwd.length >= 6) level++
  if (pwd.length >= 10) level++
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) level++
  if (/\d/.test(pwd)) level++
  if (/[^\w\s]/.test(pwd)) level++
  return Math.min(level, 4)
})

const strengthPercent = computed(() => {
  return (strengthLevel.value / 4) * 100 + '%'
})

const strengthClass = computed(() => {
  const map = ['', 'weak', 'normal', 'strong', 'very-strong']
  return map[strengthLevel.value]
})

const strengthText = computed(() => {
  const map = ['', '弱', '一般', '强', '非常强']
  return map[strengthLevel.value]
})

async function handleSubmit() {
  if (!form.oldPassword) {
    uni.showToast({ title: '请输入旧密码', icon: 'none' })
    return
  }
  if (form.newPassword.length < 6) {
    uni.showToast({ title: '新密码至少6位', icon: 'none' })
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
    return
  }
  if (form.oldPassword === form.newPassword) {
    uni.showToast({ title: '新密码不能与旧密码相同', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    uni.showToast({ title: '密码修改成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 1000)
  } catch (e: any) {
    uni.showToast({ title: e.message || '修改失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.password-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
}

.header-tip {
  padding: 24rpx 32rpx 16rpx;
}

.tip-text {
  font-size: 26rpx;
  color: #909399;
}

.form-card {
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.form-item {
  margin-bottom: 28rpx;
}

.input-wrap {
  display: flex;
  align-items: center;
  height: 96rpx;
  background: #f5f7fa;
  border: 2rpx solid transparent;
  border-radius: 12rpx;
  padding: 0 20rpx;
  transition: all 0.3s;

  &.focus {
    border-color: #409eff;
    background: #fff;
  }
}

.input-icon {
  font-size: 30rpx;
  margin-right: 12rpx;
}

.input {
  flex: 1;
  height: 96rpx;
  font-size: 28rpx;
  color: #303133;
}

.placeholder {
  color: #c0c4cc;
  font-size: 28rpx;
}

.toggle-eye {
  font-size: 28rpx;
  color: #909399;
  padding: 12rpx;
}

.strength-bar {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
  padding: 0 4rpx;
}

.strength-track {
  flex: 1;
  height: 8rpx;
  background: #ebeef5;
  border-radius: 4rpx;
  overflow: hidden;
  margin-right: 16rpx;
}

.strength-fill {
  height: 100%;
  border-radius: 4rpx;
  transition: all 0.3s;

  &.weak { background: #f56c6c; }
  &.normal { background: #e6a23c; }
  &.strong { background: #67c23a; }
  &.very-strong { background: #409eff; }
}

.strength-text {
  font-size: 22rpx;

  &.weak { color: #f56c6c; }
  &.normal { color: #e6a23c; }
  &.strong { color: #67c23a; }
  &.very-strong { color: #409eff; }
}

.submit-btn {
  width: 100%;
  height: 96rpx;
  background: #c0c4cc;
  color: #fff;
  font-size: 30rpx;
  font-weight: bold;
  border-radius: 48rpx;
  border: none;
  margin-top: 16rpx;
  transition: all 0.3s;

  &.active {
    background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
    box-shadow: 0 8rpx 20rpx rgba(64, 158, 255, 0.4);
  }

  &::after {
    border: none;
  }
}

.tips-card {
  margin: 32rpx 24rpx 0;
  background: #fdf6ec;
  border-radius: 16rpx;
  padding: 24rpx;
  border-left: 6rpx solid #e6a23c;
}

.tips-title {
  display: block;
  font-size: 28rpx;
  font-weight: bold;
  color: #e6a23c;
  margin-bottom: 16rpx;
}

.tips-list {
  display: flex;
  flex-direction: column;
}

.tips-item {
  font-size: 24rpx;
  color: #b88230;
  line-height: 1.8;
}
</style>
