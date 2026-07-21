<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">广播策略</h2>
    </div>

    <div class="table-card">
      <div class="toolbar" style="padding: 12px 16px">
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增策略</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="handleRefresh">刷新</el-button>
      </div>
      <el-table :data="list" stripe>
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
            <span v-else-if="row.repeatType === 'custom'">自定义</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="人流量条件" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.enableFlow">
              {{ row.flowCondition === 'gt' ? '大于' : '小于' }} {{ row.flowThreshold }} 人
            </span>
            <span v-else style="color: #909399">-</span>
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
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="广播策略" width="600px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="策略名称" required>
          <el-input v-model="form.name" placeholder="请输入策略名称" />
        </el-form-item>
        <el-form-item label="关联广播" required>
          <el-select v-model="form.broadcastId" placeholder="请选择广播">
            <el-option v-for="b in broadcasts" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-select v-model="form.startTime" :picker-options="{ start: '00:00', step: '00:30', end: '23:30' }" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-select v-model="form.endTime" :picker-options="{ start: '00:00', step: '00:30', end: '23:30' }" />
        </el-form-item>
        <el-form-item label="重复类型">
          <el-select v-model="form.repeatType" placeholder="请选择重复类型">
            <el-option label="每天" value="daily" />
            <el-option label="工作日" value="weekdays" />
            <el-option label="周末" value="weekend" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <template v-if="form.repeatType === 'custom'">
          <el-form-item label="自定义方式">
            <el-radio-group v-model="form.customMode">
              <el-radio label="date">按日期</el-radio>
              <el-radio label="range">按日期范围</el-radio>
              <el-radio label="week">按星期</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.customMode === 'date'" label="选择日期">
            <el-date-picker
              v-model="form.customDates"
              type="dates"
              placeholder="选择播放日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-else-if="form.customMode === 'range'" label="日期范围">
            <el-date-picker
              v-model="form.customDateRange"
              type="daterange"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-else label="选择星期">
            <el-select v-model="form.customDays" multiple placeholder="请选择星期" style="width: 100%">
              <el-option label="周一" :value="1" />
              <el-option label="周二" :value="2" />
              <el-option label="周三" :value="3" />
              <el-option label="周四" :value="4" />
              <el-option label="周五" :value="5" />
              <el-option label="周六" :value="6" />
              <el-option label="周日" :value="7" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="人流量判断">
          <el-switch v-model="form.enableFlow" active-text="启用" inactive-text="不启用" />
        </el-form-item>
        <template v-if="form.enableFlow">
          <el-form-item label="判断条件">
            <el-radio-group v-model="form.flowCondition">
              <el-radio label="gt">大于</el-radio>
              <el-radio label="lt">小于</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="人流阈值">
            <el-input-number v-model="form.flowThreshold" :min="0" :step="1" placeholder="请输入阈值" />
            <span style="margin-left: 8px; color: #909399; font-size: 13px">人</span>
          </el-form-item>
        </template>
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
import { getStrategies, addStrategy, updateStrategy, deleteStrategy, getBroadcasts } from '@/api/broadcast'

const list = ref([])
const broadcasts = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  name: '',
  broadcastId: null,
  startTime: '18:00',
  endTime: '22:00',
  repeatType: 'daily',
  customDays: [],
  customMode: 'week',
  customDates: [],
  customDateRange: [],
  enableFlow: false,
  flowCondition: 'gt',
  flowThreshold: 0,
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

function formatCustomDays(days) {
  if (!days || !days.length) return '-'
  return days.sort().map(d => WEEK_MAP[d]).join('、')
}

function ensureTimeWithSeconds(time) {
  if (!time) return time
  if (time.length === 5) return time + ':00'
  return time
}

function stripSeconds(time) {
  if (!time) return time
  return time.length > 5 ? time.substring(0, 5) : time
}

async function loadBroadcasts() {
  try {
    const res = await getBroadcasts()
    broadcasts.value = res.data || []
  } catch (error) {
    // ignore
  }
}

async function loadData() {
  console.log('[BroadcastStrategy] loadData called')
  loading.value = true
  try {
    const res = await getStrategies()
    console.log('[BroadcastStrategy] loadData response:', res)
    list.value = res.data || []
  } catch (error) {
    console.error('[BroadcastStrategy] loadData error:', error)
    ElMessage.error('加载策略列表失败')
  } finally {
    loading.value = false
  }
}

function handleRefresh() {
  console.log('[BroadcastStrategy] handleRefresh clicked')
  loadData()
}

function openDialog(row = null) {
  dialogVisible.value = true
  loadBroadcasts()
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name,
      broadcastId: row.broadcastId,
      startTime: stripSeconds(row.startTime),
      endTime: stripSeconds(row.endTime),
      repeatType: row.repeatType || 'daily',
      customDays: row.customDays || [],
      customMode: 'week',
      customDates: [],
      customDateRange: [],
      enableFlow: row.enableFlow || false,
      flowCondition: row.flowCondition || 'gt',
      flowThreshold: row.flowThreshold || 0,
      enabled: row.enabled,
      description: row.description || ''
    })
  } else {
    Object.assign(form, {
      id: null,
      name: '',
      broadcastId: null,
      startTime: '18:00',
      endTime: '22:00',
      repeatType: 'daily',
      customDays: [],
      customMode: 'week',
      customDates: [],
      customDateRange: [],
      enableFlow: false,
      flowCondition: 'gt',
      flowThreshold: 0,
      enabled: 1,
      description: ''
    })
  }
}

async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入策略名称')
    return
  }
  if (!form.broadcastId) {
    ElMessage.warning('请选择关联广播')
    return
  }
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请选择开始时间和结束时间')
    return
  }
  if (form.repeatType === 'custom') {
    if (form.customMode === 'date') {
      if (!form.customDates || form.customDates.length === 0) {
        ElMessage.warning('请至少选择一个日期')
        return
      }
    } else if (form.customMode === 'range') {
      if (!form.customDateRange || form.customDateRange.length !== 2) {
        ElMessage.warning('请选择日期范围')
        return
      }
    } else {
      if (!form.customDays || form.customDays.length === 0) {
        ElMessage.warning('请至少选择一个星期')
        return
      }
    }
  }

  saving.value = true
  const payload = {
    ...form,
    startTime: ensureTimeWithSeconds(form.startTime),
    endTime: ensureTimeWithSeconds(form.endTime)
  }

  // 自定义按日期时，将日期转换为对应星期几；后端只接收 customDays
  if (form.repeatType === 'custom' && form.customMode === 'date' && form.customDates?.length) {
    const daySet = new Set(form.customDates.map(dateStr => {
      const date = new Date(dateStr)
      const day = date.getDay()
      return day === 0 ? 7 : day
    }))
    payload.customDays = Array.from(daySet).sort((a, b) => a - b)
  }
  delete payload.customMode
  delete payload.customDates
  delete payload.customDateRange
  try {
    if (form.id) {
      await updateStrategy(payload)
      ElMessage.success('更新成功')
    } else {
      await addStrategy(payload)
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
    await ElMessageBox.confirm(`确定删除策略「${row.name}」吗？`, '提示', { type: 'warning' })
    await deleteStrategy(row.id)
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