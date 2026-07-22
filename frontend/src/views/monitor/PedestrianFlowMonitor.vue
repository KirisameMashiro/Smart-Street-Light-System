<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">人流监测</h2>
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
        <el-button :icon="Refresh" :loading="loading" @click="onManualRefresh">刷新</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select
        v-model="filter.district"
        placeholder="动物园区"
        clearable
        style="width: 160px"
        @change="onDistrictChange"
      >
        <el-option
          v-for="o in availableDistricts"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <span class="text-muted" style="margin-left: auto">
        共 {{ filteredLights.length }} 盏
        <template v-if="stats.total > 0">
          ｜有数据 {{ stats.hasData }} ｜无数据 {{ stats.noData }}
        </template>
      </span>
    </div>

    <!-- 列表 -->
    <div class="table-card">
      <div class="table-wrapper">
        <el-table :data="page.records" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="lightCode" label="编号" min-width="110" />
          <el-table-column prop="lightName" label="名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="district" label="动物园区" min-width="100" />
          <el-table-column prop="road" label="路段" min-width="100" />
          <el-table-column label="最新人流量" width="120" align="center">
            <template #default="{ row }">
              <span v-if="flowMap[row.id] != null" class="flow-value">{{ flowMap[row.id] }} 人</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="近1h平均人流量" width="130" align="center">
            <template #default="{ row }">
              <span v-if="avgFlowMap[row.id] != null" class="flow-value">{{ avgFlowMap[row.id] }} 人</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="采集时间" min-width="160">
            <template #default="{ row }">
              <span v-if="flowCollectTime[row.id]" class="text-muted" style="font-size: 13px">
                {{ flowCollectTime[row.id] }}
              </span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="goToDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="page.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadPage"
          @current-change="loadPage"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'PedestrianFlowMonitor' })
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getAllLights } from '@/api/light'
import {
  getAllLatestPedestrianFlow,
  getAveragePedestrianFlow
} from '@/api/pedestrian-flow'
import { getSystemDistricts, getSystemRoads } from '@/api/system'
import { useAppStore } from '@/store/app'

const appStore = useAppStore()
const router = useRouter()

const loading = ref(false)
const autoRefresh = ref(false)
const interval = ref(5)
const query = reactive({ pageNum: 1, pageSize: 10 })

const allLights = ref([])
const flowMap = reactive({})
const avgFlowMap = reactive({})
const flowCollectTime = reactive({})
const districtOptions = ref([])
const roadOptions = ref([])
const districtRoadMap = ref({})

const filter = reactive({ district: undefined })

const filteredLights = computed(() => {
  return allLights.value.filter((d) => {
    if (filter.district && d.district !== filter.district) return false
    return true
  })
})

const page = computed(() => {
  const data = filteredLights.value
  const total = data.length
  const start = (query.pageNum - 1) * query.pageSize
  const end = Math.min(start + query.pageSize, total)
  return {
    records: data.slice(start, end),
    total
  }
})

const availableDistricts = computed(() => {
  const districts = new Set()
  allLights.value.forEach((l) => { if (l.district) districts.add(l.district) })
  return Array.from(districts).map((d) => ({ value: d, label: d }))
})

const stats = computed(() => {
  let hasData = 0, noData = 0
  allLights.value.forEach((l) => {
    if (flowMap[l.id] != null) hasData++
    else noData++
  })
  return { total: allLights.value.length, hasData, noData }
})

function onDistrictChange() {
  query.pageNum = 1
}

function applyFilter() {
  query.pageNum = 1
}

function goToDetail(row) {
  router.push(`/devices/${row.id}?from=monitor`)
}

async function loadAllLights() {
  try {
    const res = await getAllLights()
    // 只显示有监控（hasCamera=true）的路灯
    allLights.value = (res.data || []).filter((l) => l.hasCamera)
  } catch (e) {
    allLights.value = []
  }
}

async function loadDistrictsAndRoads() {
  try {
    const dRes = await getSystemDistricts()
    districtOptions.value = (dRes.data || []).map((d) => ({ value: d.districtName, label: d.districtName }))
  } catch (e) {}
}

async function loadFlowData() {
  try {
    const res = await getAllLatestPedestrianFlow()
    const data = res.data || {}
    Object.keys(flowMap).forEach((k) => delete flowMap[k])
    Object.keys(flowCollectTime).forEach((k) => delete flowCollectTime[k])
    for (const [lightId, value] of Object.entries(data)) {
      const id = Number(lightId)
      flowMap[id] = value.flowCount ?? 0
      flowCollectTime[id] = value.collectTime ? value.collectTime.replace('T', ' ').substring(0, 19) : '-'
    }
  } catch (e) {}
}

async function loadAvgFlow() {
  try {
    const promises = allLights.value
      .filter((l) => flowMap[l.id] != null)
      .slice(0, 50) // 最多请求50盏灯的近1h平均
      .map(async (l) => {
        try {
          const res = await getAveragePedestrianFlow(l.id)
          if (res.data && res.data.flowCount != null) {
            avgFlowMap[l.id] = res.data.flowCount
          }
        } catch (e) {}
      })
    await Promise.all(promises)
  } catch (e) {}
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadAllLights(), loadFlowData(), loadDistrictsAndRoads()])
    await loadAvgFlow()
  } finally {
    loading.value = false
  }
}

function onManualRefresh() {
  loadAll()
}

// 自动轮询
let pollTimer = null
function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  pollTimer = setInterval(() => {
    loadAll()
  }, interval.value * 1000)
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(autoRefresh, (val) => {
  if (val) startPolling()
  else stopPolling()
})

watch(() => appStore.lightDataVersion, () => {
  loadAll()
})

onMounted(() => {
  loadAll()
  if (autoRefresh.value) startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.flow-value {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}
</style>