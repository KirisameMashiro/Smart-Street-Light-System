<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">广播设计</h2>
    </div>

    <div class="table-card">
      <div class="toolbar" style="padding: 12px 16px">
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增广播</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="handleRefresh">刷新</el-button>
      </div>
      <el-table :data="list" stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="title" label="广播主题" width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="广播内容" min-width="250" show-overflow-tooltip />
        <el-table-column label="关联路灯" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.lightCodes || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="语音" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.voiceFilePath" type="success" size="small">已生成</el-tag>
            <el-tag v-else type="info" size="small">未生成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" class-name="table-ops">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="广播设计" width="650px" :close-on-click-modal="false" :before-close="handleDialogClose">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="广播主题" required>
          <el-input v-model="form.title" placeholder="请输入广播主题" />
        </el-form-item>
        <el-form-item label="广播内容">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请输入广播内容" />
        </el-form-item>
        <el-form-item label="关联路灯">
          <el-select
            v-model="form.lightIds"
            multiple
            filterable
            remote
            reserve-keyword
            placeholder="请输入路灯编号或名称搜索"
            :remote-method="searchLights"
            :loading="lightLoading"
            style="width: 100%"
          >
            <el-option
              v-for="item in lightOptions"
              :key="item.id"
              :label="`${item.lightCode} (${item.lightName || item.location || '未命名'})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>

        <el-divider content-position="left">
          <span style="font-size: 14px; font-weight: 500; color: #303133">语音设置</span>
        </el-divider>

        <el-form-item label="语音角色">
          <el-select v-model="form.voiceName" placeholder="请选择语音角色" style="width: 220px">
            <el-option label="亲和-女 (Serena)" value="Serena" />
            <el-option label="明亮-女 (Vivian)" value="Vivian" />
            <el-option label="沉稳-男 (Uncle Fu)" value="Uncle_Fu" />
            <el-option label="Dylan" value="Dylan" />
            <el-option label="Eric" value="Eric" />
            <el-option label="Ryan" value="Ryan" />
            <el-option label="Aiden" value="Aiden" />
            <el-option label="Ono Anna" value="Ono_Anna" />
            <el-option label="Sohee" value="Sohee" />
          </el-select>
        </el-form-item>

        <el-form-item label="语速">
          <el-slider
            v-model="form.voiceSpeed"
            :min="0.5"
            :max="2.0"
            :step="0.1"
            show-input
            style="width: 300px"
          />
          <span class="form-unit">{{ (Number(form.voiceSpeed) || 1.0).toFixed(1) }}x</span>
        </el-form-item>

        <el-form-item label="音量">
          <el-slider
            v-model="form.voiceVolume"
            :min="0"
            :max="100"
            :step="5"
            show-input
            style="width: 300px"
          />
          <span class="form-unit">{{ Number(form.voiceVolume) || 80 }}%</span>
        </el-form-item>

        <el-form-item label="语音生成">
          <el-button
            type="success"
            :icon="VideoPlay"
            :loading="generating"
            :disabled="!form.content"
            @click="onGenerateVoice"
          >
            {{ generating ? '生成中…' : '生成语音' }}
          </el-button>
          <span v-if="generating" class="form-tip" style="color: #e6a23c">
            ⏳ AI 正在合成语音，请耐心等待，不要关闭窗口...
          </span>
          <span v-else-if="form.voiceFilePath" class="form-tip" style="color: #67c23a">
            ✅ 已生成
          </span>
          <span v-else class="form-tip" style="color: #909399">
            生成后可用于广播播放
          </span>
        </el-form-item>

        <el-form-item v-if="audioUrl" label="试听">
          <audio :src="audioUrl" controls style="width: 100%; max-width: 400px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCancel" :disabled="generating">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="generating" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, VideoPlay } from '@element-plus/icons-vue'
import { getBroadcasts, addBroadcast, updateBroadcast, deleteBroadcast, generateVoice, getVoiceFileUrl } from '@/api/broadcast'
import { getAllLights } from '@/api/light'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const generating = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const audioUrl = ref('')

const allLights = ref([])
const lightOptions = ref([])
const lightLoading = ref(false)

const form = reactive({
  id: null,
  title: '',
  content: '',
  lightIds: [],
  lightCodes: '',
  enabled: 1,
  description: '',
  voiceName: 'Serena',
  voiceSpeed: 1.0,
  voiceVolume: 80,
  voiceFilePath: ''
})

function formatTime(time) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

async function loadAllLights() {
  try {
    const res = await getAllLights()
    allLights.value = (res.data || []).filter(l => l.hasSpeaker === true || l.hasSpeaker === 1)
    lightOptions.value = allLights.value
  } catch (error) {
    // ignore
  }
}

function searchLights(query) {
  if (query) {
    const lower = query.toLowerCase()
    lightOptions.value = allLights.value.filter(l =>
      (l.lightCode && l.lightCode.toLowerCase().includes(lower)) ||
      (l.lightName && l.lightName.toLowerCase().includes(lower)) ||
      (l.location && l.location.toLowerCase().includes(lower))
    )
  } else {
    lightOptions.value = allLights.value
  }
}

function syncLightCodes() {
  if (!form.lightIds || form.lightIds.length === 0) {
    form.lightCodes = ''
    return
  }
  const codes = form.lightIds
    .map(id => allLights.value.find(l => l.id === id))
    .filter(Boolean)
    .map(l => l.lightCode)
  form.lightCodes = codes.join(',')
}

async function loadData() {
  console.log('[BroadcastDesign] loadData called')
  loading.value = true
  try {
    const res = await getBroadcasts()
    console.log('[BroadcastDesign] loadData response:', res)
    list.value = res.data || []
  } catch (error) {
    console.error('[BroadcastDesign] loadData error:', error)
    ElMessage.error('加载广播列表失败')
  } finally {
    loading.value = false
  }
}

function handleRefresh() {
  console.log('[BroadcastDesign] handleRefresh clicked')
  loadData()
}

async function handleDialogClose(done) {
  if (generating.value) {
    try {
      await ElMessageBox.confirm(
        '语音正在生成中，关闭窗口将丢弃生成结果。确定要退出吗？',
        '语音生成中',
        { confirmButtonText: '等待完成', cancelButtonText: '强制退出', type: 'warning' }
      )
      // 用户选择等待 → 不关闭
    } catch {
      // 用户选择强制退出 → 关闭
      done()
      return
    }
  } else {
    done()
  }
}

function handleCancel() {
  if (generating.value) {
    ElMessage.warning('语音正在生成中，请等待完成后再关闭')
    return
  }
  dialogVisible.value = false
}

function openDialog(row = null) {
  dialogVisible.value = true
  // 清理上一次的试听 URL
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = ''
  }
  loadAllLights()
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title,
      content: row.content,
      lightIds: row.lightIds || [],
      lightCodes: row.lightCodes || '',
      enabled: row.enabled,
      description: row.description || '',
      voiceName: row.voiceName || 'Serena',
      voiceSpeed: row.voiceSpeed != null ? Number(row.voiceSpeed) : 1.0,
      voiceVolume: row.voiceVolume != null ? Number(row.voiceVolume) : 80,
      voiceFilePath: row.voiceFilePath || ''
    })
    // 如果有已生成的语音文件，加载预览
    if (row.voiceFilePath) {
      audioUrl.value = getVoiceFileUrl(row.id)
    }
  } else {
    Object.assign(form, {
      id: null,
      title: '',
      content: '',
      lightIds: [],
      lightCodes: '',
      enabled: 1,
      description: '',
      voiceName: 'Serena',
      voiceSpeed: 1.0,
      voiceVolume: 80,
      voiceFilePath: ''
    })
  }
}

async function onGenerateVoice() {
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('请先填写广播内容')
    return
  }

  // 先保存当前表单（如果是新广播需要先创建，如果是编辑就更新）
  syncLightCodes()
  saving.value = true
  try {
    const payload = {
      title: form.title || '未命名广播',
      content: form.content,
      lightIds: form.lightIds,
      lightCodes: form.lightCodes,
      enabled: form.enabled,
      description: form.description,
      voiceName: form.voiceName,
      voiceSpeed: form.voiceSpeed,
      voiceVolume: form.voiceVolume
    }

    if (!form.id) {
      // 新广播：先创建，返回的 data 包含自动生成的 ID
      const addRes = await addBroadcast(payload)
      if (addRes.data && addRes.data.id) {
        form.id = addRes.data.id
      } else {
        ElMessage.error('创建广播失败，无法生成语音')
        return
      }
    } else {
      // 已存在：更新广播
      payload.id = form.id
      await updateBroadcast(payload)
    }
  } catch (error) {
    ElMessage.error('保存失败：' + (error.response?.data?.message || error.message))
    return
  } finally {
    saving.value = false
  }

  // 生成语音
  generating.value = true
  try {
    const res = await generateVoice(form.id)
    form.voiceFilePath = res.data
    ElMessage.success('语音生成成功')

    // 加载试听
    if (audioUrl.value) {
      URL.revokeObjectURL(audioUrl.value)
    }
    audioUrl.value = getVoiceFileUrl(form.id)
  } catch (error) {
    ElMessage.error('语音生成失败：' + (error.response?.data?.message || error.message))
  } finally {
    generating.value = false
  }
}

async function onSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入广播主题')
    return
  }
  syncLightCodes()
  saving.value = true
  try {
    const payload = {
      id: form.id || undefined,
      title: form.title,
      content: form.content,
      lightIds: form.lightIds,
      lightCodes: form.lightCodes,
      enabled: form.enabled,
      description: form.description,
      voiceName: form.voiceName,
      voiceSpeed: form.voiceSpeed,
      voiceVolume: form.voiceVolume
    }
    if (form.id) {
      await updateBroadcast(payload)
      ElMessage.success('更新成功')
    } else {
      await addBroadcast(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除广播「${row.title}」吗？`, '提示', { type: 'warning' })
    await deleteBroadcast(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
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
