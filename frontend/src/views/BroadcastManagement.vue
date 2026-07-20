<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">广播管理</h2>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <el-tab-pane label="广播设计" name="broadcast">
        <div class="table-card">
          <div class="toolbar" style="padding: 12px 16px">
            <el-button type="primary" :icon="Plus" @click="openBroadcastDialog()">新增广播</el-button>
            <el-button :icon="Refresh" :loading="broadcastLoading" @click="loadBroadcasts">刷新</el-button>
          </div>
          <el-table :data="broadcasts" stripe>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="title" label="广播主题" width="200" show-overflow-tooltip />
            <el-table-column prop="content" label="广播内容" min-width="250" show-overflow-tooltip />
            <el-table-column prop="lightCodes" label="关联路灯" min-width="180" show-overflow-tooltip />
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
                <el-button link type="primary" @click="openBroadcastDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="onBroadcastDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="广播策略" name="strategy">
        <div class="table-card">
          <div class="toolbar" style="padding: 12px 16px">
            <el-button type="primary" :icon="Plus" @click="openStrategyDialog()">新增策略</el-button>
            <el-button :icon="Refresh" :loading="strategyLoading" @click="loadStrategies">刷新</el-button>
          </div>
          <el-table :data="strategies" stripe>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="name" label="策略名称" width="180" show-overflow-tooltip />
            <el-table-column prop="broadcastTitle" label="关联广播" width="180" show-overflow-tooltip />
            <el-table-column label="适用时间" width="180">
              <template #default="{ row }">
                {{ row.startTime }} - {{ row.endTime }}
              </template>
            </el-table-column>
            <el-table-column label="重复类型" width="140">
              <template #default="{ row }">
                {{ REPEAT_TYPE_MAP[row.repeatType] || row.repeatType || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="自定义星期" width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.repeatType === 'custom' && row.customDays?.length">
                  {{ formatCustomDays(row.customDays) }}
                </span>
                <span v-else>-</span>
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
            <el-table-column label="操作" width="160" fixed="right" class-name="table-ops">
              <template #default="{ row }">
                <el-button link type="primary" @click="openStrategyDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="onStrategyDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="broadcastDialogVisible" title="广播设计" width="600px">
      <el-form ref="broadcastFormRef" :model="broadcastForm" label-width="100px">
        <el-form-item label="广播主题" required>
          <el-input v-model="broadcastForm.title" placeholder="请输入广播主题" />
        </el-form-item>
        <el-form-item label="广播内容">
          <el-input v-model="broadcastForm.content" type="textarea" :rows="4" placeholder="请输入广播内容" />
        </el-form-item>
        <el-form-item label="关联路灯编号">
          <el-input v-model="broadcastForm.lightCodes" placeholder="多个路灯编号用逗号分隔，如：L001,L002" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="broadcastForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="broadcastForm.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="broadcastDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="broadcastSaving" @click="onSaveBroadcast">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="strategyDialogVisible" title="广播策略" width="600px">
      <el-form ref="strategyFormRef" :model="strategyForm" label-width="100px">
        <el-form-item label="策略名称" required>
          <el-input v-model="strategyForm.name" placeholder="请输入策略名称" />
        </el-form-item>
        <el-form-item label="关联广播" required>
          <el-select v-model="strategyForm.broadcastId" placeholder="请选择广播">
            <el-option v-for="b in broadcasts" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-select v-model="strategyForm.startTime" :picker-options="{ start: '00:00', step: '00:30', end: '23:30' }" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-select v-model="strategyForm.endTime" :picker-options="{ start: '00:00', step: '00:30', end: '23:30' }" />
        </el-form-item>
        <el-form-item label="重复类型">
          <el-select v-model="strategyForm.repeatType" placeholder="请选择重复类型">
            <el-option label="每天" value="daily" />
            <el-option label="工作日" value="weekdays" />
            <el-option label="周末" value="weekend" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="自定义星期" v-if="strategyForm.repeatType === 'custom'">
          <el-select v-model="strategyForm.customDays" multiple placeholder="请选择星期">
            <el-option label="周一" :value="1" />
            <el-option label="周二" :value="2" />
            <el-option label="周三" :value="3" />
            <el-option label="周四" :value="4" />
            <el-option label="周五" :value="5" />
            <el-option label="周六" :value="6" />
            <el-option label="周日" :value="7" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="strategyForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="strategyForm.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="strategyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="strategySaving" @click="onSaveStrategy">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  getBroadcasts, addBroadcast, updateBroadcast, deleteBroadcast,
  getStrategies, addStrategy, updateStrategy, deleteStrategy
} from '@/api/broadcast'

const activeTab = ref('broadcast')

const broadcasts = ref([])
const strategies = ref([])
const broadcastLoading = ref(false)
const strategyLoading = ref(false)
const broadcastSaving = ref(false)
const strategySaving = ref(false)

const broadcastDialogVisible = ref(false)
const strategyDialogVisible = ref(false)
const broadcastFormRef = ref(null)
const strategyFormRef = ref(null)

const broadcastForm = reactive({
  id: null,
  title: '',
  content: '',
  lightIds: [],
  lightCodes: '',
  enabled: 1,
  description: ''
})

const strategyForm = reactive({
  id: null,
  name: '',
  broadcastId: null,
  startTime: '',
  endTime: '',
  repeatType: 'daily',
  customDays: [],
  enabled: 1,
  description: ''
})

const REPEAT_TYPE_MAP = {
  daily: '每天',
  weekdays: '工作日',
  weekend: '周末',
  custom: '自定义'
}

const WEEK_MAP = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日'
}

function formatTime(time) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

function formatCustomDays(days) {
  if (!days || !days.length) return '-'
  return days.sort().map(d => WEEK_MAP[d]).join('、')
}

async function loadBroadcasts() {
  broadcastLoading.value = true
  try {
    const res = await getBroadcasts()
    broadcasts.value = res.data || []
  } catch (error) {
    ElMessage.error('加载广播列表失败')
  } finally {
    broadcastLoading.value = false
  }
}

async function loadStrategies() {
  strategyLoading.value = true
  try {
    const res = await getStrategies()
    strategies.value = res.data || []
  } catch (error) {
    ElMessage.error('加载策略列表失败')
  } finally {
    strategyLoading.value = false
  }
}

function openBroadcastDialog(row = null) {
  broadcastDialogVisible.value = true
  if (row) {
    Object.assign(broadcastForm, {
      id: row.id,
      title: row.title,
      content: row.content,
      lightIds: row.lightIds || [],
      lightCodes: row.lightCodes || '',
      enabled: row.enabled,
      description: row.description || ''
    })
  } else {
    Object.assign(broadcastForm, {
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

async function onSaveBroadcast() {
  if (!broadcastForm.title.trim()) {
    ElMessage.warning('请输入广播主题')
    return
  }
  broadcastSaving.value = true
  try {
    if (broadcastForm.id) {
      await updateBroadcast(broadcastForm)
      ElMessage.success('更新成功')
    } else {
      await addBroadcast(broadcastForm)
      ElMessage.success('新增成功')
    }
    broadcastDialogVisible.value = false
    loadBroadcasts()
    loadStrategies()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    broadcastSaving.value = false
  }
}

async function onBroadcastDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除广播「${row.title}」吗？`, '提示', { type: 'warning' })
    await deleteBroadcast(row.id)
    ElMessage.success('删除成功')
    loadBroadcasts()
    loadStrategies()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function openStrategyDialog(row = null) {
  strategyDialogVisible.value = true
  if (row) {
    Object.assign(strategyForm, {
      id: row.id,
      name: row.name,
      broadcastId: row.broadcastId,
      startTime: row.startTime,
      endTime: row.endTime,
      repeatType: row.repeatType || 'daily',
      customDays: row.customDays || [],
      enabled: row.enabled,
      description: row.description || ''
    })
  } else {
    Object.assign(strategyForm, {
      id: null,
      name: '',
      broadcastId: null,
      startTime: '18:00',
      endTime: '22:00',
      repeatType: 'daily',
      customDays: [],
      enabled: 1,
      description: ''
    })
  }
}

async function onSaveStrategy() {
  if (!strategyForm.name.trim()) {
    ElMessage.warning('请输入策略名称')
    return
  }
  if (!strategyForm.broadcastId) {
    ElMessage.warning('请选择关联广播')
    return
  }
  if (!strategyForm.startTime || !strategyForm.endTime) {
    ElMessage.warning('请选择开始时间和结束时间')
    return
  }
  strategySaving.value = true
  try {
    if (strategyForm.id) {
      await updateStrategy(strategyForm)
      ElMessage.success('更新成功')
    } else {
      await addStrategy(strategyForm)
      ElMessage.success('新增成功')
    }
    strategyDialogVisible.value = false
    loadStrategies()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    strategySaving.value = false
  }
}

async function onStrategyDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除策略「${row.name}」吗？`, '提示', { type: 'warning' })
    await deleteStrategy(row.id)
    ElMessage.success('删除成功')
    loadStrategies()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadBroadcasts()
  loadStrategies()
})
</script>