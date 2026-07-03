<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">
        报警管理
        <el-tag v-if="unhandled > 0" type="danger" size="small" style="margin-left:8px">
          {{ unhandled }} 条未处理
        </el-tag>
      </h2>
      <div class="toolbar">
        <span class="text-muted">自动刷新</span>
        <el-switch v-model="autoRefresh" />
        <el-input-number
          v-model="interval"
          :min="1"
          :max="60"
          :step="1"
          size="small"
          style="width: 110px"
        />
        <span class="text-muted">秒</span>
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select
        v-model="query.lightId"
        placeholder="选择路灯"
        clearable
        filterable
        style="width: 200px"
        @change="onSearch"
      >
        <el-option
          v-for="l in lightOptions"
          :key="l.id"
          :label="`${l.lightCode} - ${l.lightName || ''}`"
          :value="l.id"
        />
      </el-select>
      <el-select v-model="query.alertType" placeholder="报警类型" clearable style="width:150px" @change="onSearch">
        <el-option v-for="(label, key) in ALERT_TYPE_MAP" :key="key" :label="label" :value="Number(key)" />
      </el-select>
      <el-select v-model="query.alertLevel" placeholder="报警级别" clearable style="width:140px" @change="onSearch">
        <el-option
          v-for="(item, key) in ALERT_LEVEL_MAP"
          :key="key"
          :label="item.label"
          :value="Number(key)"
        />
      </el-select>
      <el-select v-model="query.status" placeholder="处理状态" clearable style="width:140px" @change="onSearch">
        <el-option
          v-for="(item, key) in ALERT_STATUS_MAP"
          :key="key"
          :label="item.label"
          :value="Number(key)"
        />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="所属路灯" width="170">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push(`/lights/${row.lightId}`)">
              {{ lightNameOf(row.lightId) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="报警类型" width="110">
          <template #default="{ row }">
            {{ ALERT_TYPE_MAP[row.alertType] || '其他' }}
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="ALERT_LEVEL_MAP[row.alertLevel]?.type" size="small">
              {{ ALERT_LEVEL_MAP[row.alertLevel]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="报警内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="100" />
        <el-table-column label="报警时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              link
              type="primary"
              @click="openHandle(row)"
            >处理</el-button>
            <el-button v-else link type="info" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 处理报警弹窗 -->
    <el-dialog v-model="handleVisible" title="处理报警" width="520px">
      <el-descriptions :column="1" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="所属路灯">{{ lightNameOf(current.lightId) }}</el-descriptions-item>
        <el-descriptions-item label="报警类型">{{ ALERT_TYPE_MAP[current.alertType] || '其他' }}</el-descriptions-item>
        <el-descriptions-item label="报警级别">
          <el-tag :type="ALERT_LEVEL_MAP[current.alertLevel]?.type" size="small">
            {{ ALERT_LEVEL_MAP[current.alertLevel]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="报警内容">{{ current.message || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报警时间">{{ formatDateTime(current.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="handleForm" label-width="80px">
        <el-form-item label="处理人">
          <el-input v-model="handleForm.handler" placeholder="处理人" />
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="handleForm.handleRemark" type="textarea" :rows="3" placeholder="处理说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmitHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="报警详情" width="520px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="所属路灯">{{ lightNameOf(current.lightId) }}</el-descriptions-item>
        <el-descriptions-item label="报警类型">{{ ALERT_TYPE_MAP[current.alertType] || '其他' }}</el-descriptions-item>
        <el-descriptions-item label="报警级别">
          <el-tag :type="ALERT_LEVEL_MAP[current.alertLevel]?.type" size="small">
            {{ ALERT_LEVEL_MAP[current.alertLevel]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="报警内容">{{ current.message || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="current.status === 1 ? 'success' : 'danger'" size="small">
            {{ current.status === 1 ? '已处理' : '未处理' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处理人">{{ current.handler || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ formatDateTime(current.handleTime) }}</el-descriptions-item>
        <el-descriptions-item label="处理备注">{{ current.handleRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报警时间">{{ formatDateTime(current.createTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { getAlertPage, handleAlert, getUnhandledCount, connectAlertSocket } from '@/api/alert'
import { getAllLights } from '@/api/light'
import { useUserStore } from '@/store/user'
import { ALERT_TYPE_MAP, ALERT_LEVEL_MAP, ALERT_STATUS_MAP } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()
const loading = ref(false)
const autoRefresh = ref(false)
const interval = ref(5)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const unhandled = ref(0)
const lightOptions = ref([])
let timer = null

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  lightId: undefined,
  alertType: undefined,
  alertLevel: undefined,
  status: undefined
})

function lightNameOf(id) {
  const l = lightOptions.value.find((x) => x.id === id)
  return l ? `${l.lightCode} - ${l.lightName || ''}` : id
}

async function loadLights() {
  const res = await getAllLights()
  lightOptions.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAlertPage(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadUnhandled() {
  try {
    const res = await getUnhandledCount()
    unhandled.value = res.data || 0
  } catch (e) {
    // ignore
  }
}

function loadAll() {
  loadData()
  loadUnhandled()
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.lightId = undefined
  query.alertType = undefined
  query.alertLevel = undefined
  query.status = undefined
  onSearch()
}

// 处理报警
const handleVisible = ref(false)
const detailVisible = ref(false)
const current = ref({})
const handleForm = reactive({ handler: '', handleRemark: '' })

function openHandle(row) {
  current.value = row
  handleForm.handler = userStore.user?.realName || userStore.user?.username || ''
  handleForm.handleRemark = ''
  handleVisible.value = true
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

async function onSubmitHandle() {
  if (!handleForm.handler) {
    ElMessage.warning('请输入处理人')
    return
  }
  submitting.value = true
  try {
    await handleAlert(current.value.id, { ...handleForm })
    ElMessage.success('处理成功')
    handleVisible.value = false
    loadAll()
  } finally {
    submitting.value = false
  }
}

function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    loadAll()
  }, interval.value * 1000)
}
function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, (v) => {
  if (v) startPolling()
  else stopPolling()
})
watch(interval, () => {
  if (autoRefresh.value) startPolling()
})

let ws = null

function connectWS() {
  ws = connectAlertSocket(
    (data) => {
      // 收到新告警推送：刷新列表和未处理计数
      if (data && data.id) {
        loadAll()
        ElMessage.info(`新告警: ${data.message?.substring(0, 50) || ''}...`)
      }
    },
    (error) => {
      console.error('[Alerts] WebSocket error:', error)
    }
  )
}

onMounted(async () => {
  await loadLights()
  loadAll()
  startPolling()
  connectWS()
})

onUnmounted(() => {
  stopPolling()
  if (ws) {
    ws.onclose = null  // 阻止自动重连
    ws.close()
  }
})
</script>
