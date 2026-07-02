<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">实时监测</h2>
      <div class="toolbar">
        <el-radio-group v-model="mode">
          <el-radio-button value="list">列表模式</el-radio-button>
          <el-radio-button value="map">地图模式</el-radio-button>
        </el-radio-group>
        <el-divider direction="vertical" />
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
        placeholder="行政区"
        clearable
        style="width: 140px"
        @change="onFilterChange"
      >
        <el-option
          v-for="o in DISTRICT_OPTIONS"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <el-select
        v-model="filter.road"
        placeholder="路段"
        clearable
        style="width: 140px"
        @change="onFilterChange"
      >
        <el-option
          v-for="o in ROAD_OPTIONS"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <span class="text-muted" style="margin-left: auto">
        共 {{ filteredLights.length }} 盏
        <template v-if="mode === 'list'">
          ｜在线 {{ onlineCount }} ｜故障 {{ faultCount }}
        </template>
      </span>
    </div>

    <!-- 列表模式 -->
    <div v-if="mode === 'list'" class="table-card">
      <el-table :data="pagedLights" v-loading="loading" stripe>
        <el-table-column prop="lightCode" label="编号" width="110" />
        <el-table-column prop="lightName" label="名称" width="130" show-overflow-tooltip />
        <el-table-column prop="location" label="位置" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag
              :type="LIGHT_STATUS_MAP[row.status]?.type"
              size="small"
              :effect="row.status === 2 ? 'dark' : 'light'"
            >
              {{ LIGHT_STATUS_MAP[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最新光照(lux)" width="120">
          <template #default="{ row }">
            {{ sensorMap[row.id]?.illuminance ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="最新功率(W)" width="120">
          <template #default="{ row }">
            {{ sensorMap[row.id]?.power ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="累计耗电" width="100">
          <template #default>
            <span class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" class-name="table-ops">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              link
              type="success"
              @click="onSwitch(row, 1)"
            >开灯</el-button>
            <el-button
              v-else
              link
              type="info"
              @click="onSwitch(row, 0)"
            >关灯</el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status === 2"
              @click="openDim(row)"
            >调光</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="listQuery.pageNum"
          v-model:page-size="listQuery.pageSize"
          :total="filteredLights.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>

    <!-- 地图模式 -->
    <div v-else class="table-card">
      <div ref="mapRef" class="map-box"></div>
    </div>

    <!-- 调光弹窗 -->
    <el-dialog v-model="dimDialog" title="调光控制" width="380px">
      <div v-if="dimRow" style="text-align: center">
        <div style="margin-bottom: 16px; color: #606266">
          <b>{{ dimRow.lightCode }}</b>
          <span v-if="dimRow.lightName"> - {{ dimRow.lightName }}</span>
        </div>
        <el-slider
          v-model="dimValue"
          :min="0"
          :max="100"
          :disabled="dimRow.status === 2"
          style="padding: 0 16px"
        />
        <div style="margin-top: 8px; font-size: 13px; color: #909399">
          当前亮度：{{ dimValue }}%
        </div>
      </div>
      <template #footer>
        <el-button @click="dimDialog = false">取消</el-button>
        <el-button type="primary" :loading="dimLoading" @click="onDim">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getAllLights,
  batchSwitchLight,
  setLightBrightness
} from '@/api/light'
import { getLatestSensorData } from '@/api/sensor'
import {
  LIGHT_STATUS_MAP,
  DISTRICT_OPTIONS,
  ROAD_OPTIONS,
  STATUS_COLORS
} from '@/utils/constants'
import { logOperation } from '@/utils/log'

const loading = ref(false)
const mode = ref('list')
const autoRefresh = ref(false)
const interval = ref(5)

const allLights = ref([])
const sensorMap = reactive({})
// 传感器接口可用性：null=未知 true=可用 false=缺失（避免轮询重复弹错）
const sensorApiAvailable = ref(null)

const filter = reactive({ district: undefined, road: undefined })
const listQuery = reactive({ pageNum: 1, pageSize: 10 })

const filteredLights = computed(() =>
  allLights.value.filter((l) => {
    if (filter.district && l.district !== filter.district) return false
    if (filter.road && l.road !== filter.road) return false
    return true
  })
)

const pagedLights = computed(() => {
  const start = (listQuery.pageNum - 1) * listQuery.pageSize
  return filteredLights.value.slice(start, start + listQuery.pageSize)
})

const onlineCount = computed(
  () => filteredLights.value.filter((l) => l.status === 1).length
)
const faultCount = computed(
  () => filteredLights.value.filter((l) => l.status === 2).length
)

function applySensor(id, d) {
  if (!d) {
    delete sensorMap[id]
    return
  }
  sensorMap[id] = {
    illuminance: d.illuminance,
    power: d.power,
    collectTime: d.collectTime
  }
}

// 并发执行（限制并发数）
async function runConcurrent(items, limit, fn) {
  const queue = [...items]
  async function worker() {
    while (queue.length) {
      const item = queue.shift()
      if (!item) break
      try {
        await fn(item)
      } catch (e) {
        // 单项失败忽略
      }
    }
  }
  const workers = Array.from(
    { length: Math.min(limit, items.length) },
    worker
  )
  await Promise.all(workers)
}

// 探测并加载传感器数据：先取首盏探测接口是否可用，避免接口缺失时大量重复弹错
async function loadSensorData(lights) {
  if (!lights || lights.length === 0) return
  if (sensorApiAvailable.value === false) return

  // 首次：用第一盏探测
  if (sensorApiAvailable.value === null) {
    try {
      const res = await getLatestSensorData(lights[0].id)
      sensorApiAvailable.value = true
      applySensor(lights[0].id, res.data)
    } catch (e) {
      // 接口缺失：标记后跳过其余，不再重复弹错
      sensorApiAvailable.value = false
      return
    }
  }

  // 并发加载其余（限制 8）
  await runConcurrent(lights.slice(1), 8, async (l) => {
    try {
      const res = await getLatestSensorData(l.id)
      applySensor(l.id, res.data)
    } catch (e) {
      // 单灯失败忽略
    }
  })
}

// 地图散点
const mapRef = ref()
let mapChart = null

function statusColor(status) {
  if (status === 1) return STATUS_COLORS.on
  if (status === 2) return STATUS_COLORS.fault
  return STATUS_COLORS.off
}

function renderMap() {
  if (!mapRef.value) return
  // 若 DOM 已变（模式切换后），重新初始化
  if (!mapChart || mapChart.getDom() !== mapRef.value) {
    mapChart?.dispose()
    mapChart = echarts.init(mapRef.value)
  }
  const data = filteredLights.value
    .filter((l) => l.longitude != null && l.latitude != null)
    .map((l) => ({
      name: l.lightName || l.lightCode,
      value: [Number(l.longitude), Number(l.latitude), l.status],
      location: l.location,
      code: l.lightCode
    }))
  mapChart.setOption(
    {
      tooltip: {
        trigger: 'item',
        formatter: (p) => {
          const d = p.data
          const st = LIGHT_STATUS_MAP[d.value?.[2]]?.label || '未知'
          return `<b>${d.name}</b><br/>状态：${st}<br/>位置：${d.location || '-'}<br/>编号：${d.code || '-'}`
        }
      },
      grid: { left: 50, right: 20, top: 20, bottom: 40 },
      xAxis: {
        type: 'value',
        name: '经度',
        nameLocation: 'middle',
        nameGap: 28,
        scale: true
      },
      yAxis: {
        type: 'value',
        name: '纬度',
        nameLocation: 'middle',
        nameGap: 36,
        scale: true
      },
      visualMap: {
        type: 'piecewise',
        left: 12,
        top: 12,
        dimension: 2,
        categories: [0, 1, 2],
        inRange: {
          color: [STATUS_COLORS.off, STATUS_COLORS.on, STATUS_COLORS.fault]
        },
        formatter: (v) => LIGHT_STATUS_MAP[v]?.label || String(v),
        itemWidth: 14,
        itemHeight: 14
      },
      series: [
        {
          name: '路灯',
          type: 'scatter',
          data,
          symbolSize: (val) => (val && val[2] === 2 ? 24 : 12),
          itemStyle: {
            shadowBlur: 6,
            shadowColor: 'rgba(0,0,0,0.15)'
          },
          emphasis: { focus: 'self' }
        }
      ]
    },
    true
  )
}

// 刷新：reprobe=true 时重新探测传感器接口（手动刷新用）
async function refreshAll(reprobe = false) {
  if (reprobe) sensorApiAvailable.value = null
  loading.value = true
  try {
    const res = await getAllLights()
    allLights.value = res.data || []
  } catch (e) {
    allLights.value = []
    loading.value = false
    return
  }
  if (mode.value === 'list') {
    await loadSensorData(pagedLights.value)
  } else {
    await nextTick()
    renderMap()
  }
  loading.value = false
}

function onManualRefresh() {
  refreshAll(true)
}

function onFilterChange() {
  listQuery.pageNum = 1
  if (mode.value === 'list') {
    loadSensorData(pagedLights.value)
  } else {
    nextTick(renderMap)
  }
}

// 单灯开关
async function onSwitch(row, status) {
  try {
    await batchSwitchLight([row.id], status)
    row.status = status
    if (status === 0) row.brightness = 0
    ElMessage.success(status === 1 ? '已开灯' : '已关灯')
    logOperation(
      status === 1 ? 'switch_on' : 'switch_off',
      `${status === 1 ? '开启' : '关闭'}路灯 ${row.lightCode || row.lightName}`,
      '成功'
    )
  } catch (e) {
    // 失败提示由拦截器处理
  }
}

// 调光
const dimDialog = ref(false)
const dimRow = ref(null)
const dimValue = ref(0)
const dimLoading = ref(false)

function openDim(row) {
  dimRow.value = row
  dimValue.value = row.brightness ?? 0
  dimDialog.value = true
}

async function onDim() {
  if (!dimRow.value) return
  dimLoading.value = true
  try {
    await setLightBrightness(dimRow.value.id, dimValue.value)
    dimRow.value.brightness = dimValue.value
    ElMessage.success(`亮度已调整为 ${dimValue.value}%`)
    logOperation(
      'dimming',
      `调光 ${dimRow.value.lightCode || dimRow.value.lightName} 至 ${dimValue.value}%`,
      '成功'
    )
    dimDialog.value = false
  } catch (e) {
    // 失败提示由拦截器处理
  } finally {
    dimLoading.value = false
  }
}

// 轮询
let timer = null
function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    refreshAll(false)
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

// 翻页 / 每页条数变化：重新加载当前页传感器数据
watch(
  [() => listQuery.pageNum, () => listQuery.pageSize],
  () => {
    if (mode.value === 'list') {
      loadSensorData(pagedLights.value)
    }
  }
)

// 模式切换
watch(mode, async (m) => {
  if (m === 'map') {
    await nextTick()
    renderMap()
  } else {
    await loadSensorData(pagedLights.value)
  }
})

function onResize() {
  mapChart?.resize()
}

onMounted(async () => {
  await refreshAll(false)
  startPolling()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('resize', onResize)
  mapChart?.dispose()
  mapChart = null
})
</script>

<style scoped>
.map-box {
  height: 540px;
  width: 100%;
}
</style>
