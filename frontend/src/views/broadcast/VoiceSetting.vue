<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">语音设置</h2>
    </div>

    <div class="table-card" style="max-width: 640px">
      <el-form
        ref="formRef"
        :model="form"
        label-width="120px"
        style="padding: 20px 16px"
      >
        <el-form-item label="语音角色" required>
          <el-select v-model="form.voiceName" placeholder="请选择语音角色" style="width: 100%">
            <el-option label="默认" value="default" />
            <el-option label="男声" value="male" />
            <el-option label="女声" value="female" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>

        <el-form-item label="语速">
          <el-slider
            v-model="form.speed"
            :min="0.5"
            :max="2.0"
            :step="0.1"
            show-input
            style="width: 100%"
          />
          <span class="form-unit">{{ form.speed.toFixed(1) }}x</span>
        </el-form-item>

        <el-form-item label="音量">
          <el-slider
            v-model="form.volume"
            :min="0"
            :max="100"
            :step="5"
            show-input
            style="width: 100%"
          />
          <span class="form-unit">{{ form.volume }}%</span>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="语音配置描述（可选）"
          />
        </el-form-item>

        <el-divider />

        <el-form-item label="试听文本">
          <el-input
            v-model="previewText"
            type="textarea"
            :rows="2"
            placeholder="输入要试听的文字"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="success"
            :icon="VideoPlay"
            :loading="previewLoading"
            @click="onPreview"
          >
            试听
          </el-button>
          <span v-if="previewLoading" class="form-tip">正在合成语音...</span>
        </el-form-item>

        <el-form-item v-if="audioUrl" label="播放">
          <audio :src="audioUrl" controls style="width: 100%" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="onSave">
            保存配置
          </el-button>
          <el-button @click="loadData">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import { getVoiceSetting, updateVoiceSetting, previewTts } from '@/api/voice'

defineOptions({ name: 'VoiceSetting' })

const loading = ref(false)
const saving = ref(false)
const previewLoading = ref(false)
const formRef = ref(null)
const audioUrl = ref('')
const previewText = ref('欢迎使用智慧路灯广播系统，祝您生活愉快。')

const form = reactive({
  id: null,
  voiceName: 'default',
  speed: 1.0,
  volume: 80,
  enabled: 1,
  description: ''
})

async function loadData() {
  loading.value = true
  try {
    const res = await getVoiceSetting()
    const records = res.data || []
    if (records.length > 0) {
      const item = records[0]
      form.id = item.id
      form.voiceName = item.voiceName || 'default'
      form.speed = item.speed != null ? Number(item.speed) : 1.0
      form.volume = item.volume != null ? Number(item.volume) : 80
      form.enabled = item.enabled != null ? item.enabled : 1
      form.description = item.description || ''
    }
  } catch (e) {
    // ignore — use defaults
  } finally {
    loading.value = false
  }
}

async function onSave() {
  saving.value = true
  try {
    const payload = {
      id: form.id || undefined,
      voiceName: form.voiceName,
      speed: form.speed,
      volume: form.volume,
      enabled: form.enabled,
      description: form.description
    }
    await updateVoiceSetting(payload)
    ElMessage.success('保存成功')
    await loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onPreview() {
  if (!previewText.value.trim()) {
    ElMessage.warning('请输入试听文本')
    return
  }
  previewLoading.value = true
  // 清理之前的 audio URL
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = ''
  }
  try {
    const res = await previewTts(previewText.value.trim(), {
      voiceName: form.voiceName,
      speed: form.speed,
      volume: form.volume / 100
    })
    // res.data is a Blob
    const blob = new Blob([res.data], { type: 'audio/wav' })
    audioUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    ElMessage.error('语音合成失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    previewLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.form-unit {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
  min-width: 40px;
}
.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
.el-slider {
  margin-right: 12px;
}
</style>