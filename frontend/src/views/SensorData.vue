<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">传感器数据</h2>
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
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select
        v-model="query.lightId"
        placeholder="选择路灯"
        clearable
        filterable
        style="width: 220px"
        @change="onSearch"
      >
        <el-option
          v-for="l in lightOptions"
          :key="l.id"
          :label="`${l.lightCode} - ${l.lightName || ''}`"
          :value="l.id"
        />
      </el-select>
      <el-date-picker
        v-model="startTime"
        type="datetime"
        placeholder="开始时间"
        value-format="YYYY-MM-DDTHH:mm:ss"
        style="width: 200px"
        @change="onSearch"
      />
      <span class="time-separator">至</span>
      <el-date-picker
        v-model="endTime"
        type="datetime"
        placeholder="结束时间"
        value-format="YYYY-MM-DDTHH:mm:ss"
        style="width: 200px"
        @change="onSearch"
      />
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <div class="table-wrapper">
        <el-table :data="tableData" stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="所属路灯" width="180">
          <template #default="{ row }">
            {{ lightNameOf(row.lightId) }}
          </template>
        </el-table-column>
        <el-table-column prop="illuminance" label="光照(lux)" />
        <el-table-column prop="power" label="功率(W)" />
        <el-table-column prop="voltage" label="电压(V)" />
        <el-table-column prop="current" label="电流(A)" />
        <el-table-column prop="temperature" label="温度(°C)" />
        <el-table-column prop="humidity" label="湿度(%RH)" />
        <el-table-column label="采集时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.collectTime) }}</template>
        </el-table-column>
      </el-table>
      </div>
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
  </div>
</template>

<script setup>
defineOptions({ name: 'SensorData' })
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { getSensorDataPage } from '@/api/sensor'
import { getAllLights } from '@/api/light'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const autoRefresh = ref(false)
const interval = ref(5)
const tableData = ref([])
const total = ref(0)
const lightOptions = ref([])
const startTime = ref('')
const endTime = ref('')
let timer = null

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  lightId: undefined,
  startTime: undefined,
  endTime: undefined
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
    const params = { ...query }
    params.startTime = startTime.value || undefined
    params.endTime = endTime.value || undefined
    const res = await getSensorDataPage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.lightId = undefined
  startTime.value = ''
  endTime.value = ''
  onSearch()
}

function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    loadData()
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

onMounted(async () => {
  await loadLights()
  loadData()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.time-separator {
  margin: 0 8px;
  color: #909399;
  font-size: 14px;
}

.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

@media (max-width: 768px) {
  .filter-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .filter-bar :deep(.el-date-picker) {
    width: calc(50% - 16px) !important;
  }
  .time-separator {
    display: none;
  }

  .table-wrapper {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    -ms-overflow-style: -ms-autohiding-scrollbar;
  }

  .table-wrapper::-webkit-scrollbar {
    height: 6px;
  }

  .table-wrapper::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 3px;
  }

  .table-wrapper::-webkit-scrollbar-thumb {
    background: #c1c1c1;
    border-radius: 3px;
  }
}
</style>
