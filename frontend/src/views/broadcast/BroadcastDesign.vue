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

    <el-dialog v-model="dialogVisible" title="广播设计" width="600px">
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { getBroadcasts, addBroadcast, updateBroadcast, deleteBroadcast } from '@/api/broadcast'
import { getAllLights } from '@/api/light'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

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
  description: ''
})

function formatTime(time) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

async function loadAllLights() {
  try {
    const res = await getAllLights()
    allLights.value = res.data || []
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

function openDialog(row = null) {
  dialogVisible.value = true
  loadAllLights()
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title,
      content: row.content,
      lightIds: row.lightIds || [],
      lightCodes: row.lightCodes || '',
      enabled: row.enabled,
      description: row.description || ''
    })
  } else {
    Object.assign(form, {
      id: null,
      title: '',
      content: '',
      lightIds: [],
      lightCodes: '',
      enabled: 1,
      description: ''
    })
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
    if (form.id) {
      await updateBroadcast(form)
      ElMessage.success('更新成功')
    } else {
      await addBroadcast(form)
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