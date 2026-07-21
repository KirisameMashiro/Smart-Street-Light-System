<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">历史人流趋势</h2>
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
      <el-radio-group v-model="rangeType" @change="onRangeChange">
        <el-radio-button value="1h">最近1小时</el-radio-button>
        <el-radio-button value="6h">最近6小时</el-radio-button>
        <el-radio-button value="24h">最近24小时</el-radio-button>
        <el-radio-button value="7d">最近7天</el-radio-button>
        <el-radio-button value="custom">自定义</el-radio-button>
      </el-radio-group>
      <template v-if="rangeType === 'custom'">
        <el-date-picker
          v-model="startTime"
          type="datetime"
          placeholder="开始时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 200px"
          @change="onSearch"
        />
        <span class="time-separator">至</span>
        <el-date-picker
          v-model="endTime"
          type="datetime"
          placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 200px"
          @change="onSearch"
        />
      </template>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">最高人流量</div>
        <div class="stat-value">
          <span class="stat-num">{{ stats.max.toFixed(0) }}</span>
          <span class="stat-unit">人</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">最低人流量</div>
        <div class="stat-value">
          <span class="stat-num">{{ stats.min.toFixed(0) }}</span>
          <span class="stat-unit">人</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均人流量</div>
        <div class="stat-value">
          <span class="stat-num">{{ stats.avg.toFixed(0) }}</span>
          <span class="stat-unit">人</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">数据点数</div>
        <div class="stat-value">
          <span class="stat-num">{{ stats.count }}</span>
          <span class="stat-unit">条</span>
        </div>
      </div>
    </div>

    <!-- 折线图 -->
    <div class="chart-card">
      <div ref="chartRef" class="chart-container"></div>
      <div v-if="!loading && !query.lightId" class="empty-tip">
        请选择路灯查看趋势图
      </div>
      <div v-else-if="!loading && chartData.length === 0" class="empty-tip">
        暂无数据
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <div class="table-wrapper">
        <el-table :data="tableData" stripe>
          <el-table-column type="index" label="#" width="60" />
          <el-table-column label="所属路灯" width="180">
            <template #default="{ row }">
              {{ lightNameOf(row.lightId) }}
            </template>
          </el-table-column>
          <el-table-column label="人流量" width="120" align="center">
            <template #default="{ row }">
              <span class="flow-value">{{ row.flowCount ?? '-' }}</span>
            </template>
          </el-table-column>
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
defineOptions({ name: 'PedestrianFlowTrend' })
import { ref, reactive, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getPedestrianFlowPage } from '@/api/pedestrian-flow'
import { getAllLights } from '@/api/light'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const chartRef = ref(null)
let chartInstance = null

const lightOptions = ref([])
const rangeType = ref('24h')
const startTime = ref('')
const endTime = ref('')
const chartData = ref([])
const tableData = ref([])
const total = ref(0)
const autoRefresh = ref(false)
const interval = ref(5)
let timer = null

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  lightId: undefined
})

const stats = computed(() => {
  const data = chartData.value
  if (!data.length) return { max: 0, min: 0, avg: 0, count: 0 }
  let max = -Infinity
  let min = Infinity
  let sum = 0
  for (const d of data) {
    const v = d.flowCount ?? 0
    if (v > max) max = v
    if (v < min) min = v
    sum += v
  }
  return { max, min, avg: sum / data.length, count: data.length }
})

function lightNameOf(id) {
  const l = lightOptions.value.find((x) => x.id === id)
  return l ? `${l.lightCode} - ${l.lightName || ''}` : id
}

function formatLocalDateTime(date) {
  const y = date.getFullYear()
  const mo = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${mo}-${d} ${h}:${m}:${s}`
}

function getTimeRange() {
  const now = new Date()
  let start
  switch (rangeType.value) {
    case '1h': start = new Date(now.getTime() - 60 * 60 * 1000); break
    case '6h': start = new Date(now.getTime() - 6 * 60 * 60 * 1000); break
    case '24h': start = new Date(now.getTime() - 24 * 60 * 60 * 1000); break
    case '7d': start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000); break
    case 'custom':
    default:
      return { start: startTime.value || undefined, end: endTime.value || undefined }
  }
  return { start: formatLocalDateTime(start), end: formatLocalDateTime(now) }
}

async function loadLights() {
  try {
    const res = await getAllLights()
    lightOptions.value = (res.data || []).filter((l) => l.hasCamera)
  } catch (e) {}
}

async function loadData() {
  loading.value = true
  try {
    const range = getTimeRange()
    const tableParams = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      lightId: query.lightId,
      startTime: range.start,
      endTime: range.end
    }
    const tableRes = await getPedestrianFlowPage(tableParams)
    tableData.value = tableRes.data?.records || []
    total.value = tableRes.data?.total || 0

    if (query.lightId) {
      const chartParams = {
        pageNum: 1,
        pageSize: 2000,
        lightId: query.lightId,
        startTime: range.start,
        endTime: range.end
      }
      const chartRes = await getPedestrianFlowPage(chartParams)
      const chartRecords = chartRes.data?.records || []
      chartData.value = chartRecords
        .map((r) => ({
          time: r.collectTime,
          flowCount: r.flowCount ?? 0
        }))
        .sort((a, b) => parseTime(a.time) - parseTime(b.time))
    } else {
      chartData.value = []
    }

    await nextTick()
    renderChart()
    setTimeout(() => chartInstance?.resize(), 100)
  } finally {
    loading.value = false
  }
}

function parseTime(v) {
  if (v == null) return Date.now()
  if (typeof v === 'number') return v
  if (v instanceof Date) return v.getTime()
  if (typeof v === 'string') return new Date(v.replace(' ', 'T')).getTime()
  if (Array.isArray(v) && v.length >= 3)
    return new Date(v[0], v[1] - 1, v[2], v[3] || 0, v[4] || 0, v[5] || 0).getTime()
  const t = new Date(v).getTime()
  return isNaN(t) ? Date.now() : t
}

function computeXAxisOption(points) {
  const oneHour = 3600 * 1000
  const oneDay = 24 * oneHour
  const maxTicks = 8
  let displayStart, displayEnd

  if (points && points.length > 0) {
    const times = points.map(p => p[0])
    const dataMin = Math.min(...times)
    const dataMax = Math.max(...times)
    const dataSpan = dataMax - dataMin
    if (dataSpan === 0) {
      displayStart = dataMin - 30 * 60 * 1000
      displayEnd = dataMax + 30 * 60 * 1000
    } else {
      const padding = dataSpan * 0.15
      displayStart = dataMin - padding
      displayEnd = dataMax + padding
    }
  } else {
    const range = getTimeRange()
    displayStart = parseTime(range.start)
    displayEnd = parseTime(range.end)
  }

  const span = displayEnd - displayStart
  let interval = span / maxTicks

  if (span <= oneHour) interval = Math.max(interval, 10 * 60 * 1000)
  else if (span <= 6 * oneHour) interval = Math.max(interval, oneHour)
  else if (span <= 24 * oneHour) interval = Math.max(interval, 3 * oneHour)
  else if (span <= 48 * oneHour) interval = Math.max(interval, 6 * oneHour)
  else if (span <= 7 * oneDay) interval = Math.max(interval, oneDay)
  else if (span <= 14 * oneDay) interval = Math.max(interval, 2 * oneDay)
  else if (span <= 31 * oneDay) interval = Math.max(interval, 4 * oneDay)
  else interval = Math.ceil(span / maxTicks / oneDay) * oneDay

  return {
    type: 'time', interval,
    min: displayStart, max: displayEnd,
    boundaryGap: false,
    axisLabel: {
      rotate: 0, hideOverlap: true,
      formatter: (value) => {
        const d = new Date(value)
        const mm = String(d.getMonth() + 1).padStart(2, '0')
        const dd = String(d.getDate()).padStart(2, '0')
        const hh = String(d.getHours()).padStart(2, '0')
        const mi = String(d.getMinutes()).padStart(2, '0')
        return interval >= oneDay ? `${mm}-${dd}` : `${mm}-${dd} ${hh}:${mi}`
      }
    },
    axisLine: { lineStyle: { color: '#dcdfe6' } }
  }
}

function renderChart() {
  if (!chartInstance || !chartRef.value) return
  if (!query.lightId) {
    chartInstance.setOption({
      xAxis: { type: 'time', min: null, max: null, show: false },
      yAxis: { show: false },
      series: [{ type: 'line', data: [] }]
    }, true)
    return
  }

  const points = chartData.value.map((d) => {
    const t = parseTime(d.time)
    return [t, d.flowCount ?? 0]
  })

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        const d = new Date(p.value[0])
        const mm = String(d.getMonth() + 1).padStart(2, '0')
        const dd = String(d.getDate()).padStart(2, '0')
        const hh = String(d.getHours()).padStart(2, '0')
        const mi = String(d.getMinutes()).padStart(2, '0')
        return `${mm}-${dd} ${hh}:${mi}<br/>人流量: <strong>${p.value[1]}</strong> 人`
      }
    },
    grid: { left: 60, right: 30, top: 30, bottom: 40 },
    xAxis: computeXAxisOption(points),
    yAxis: {
      type: 'value',
      name: '人流量 (人)',
      nameTextStyle: { color: '#909399' },
      axisLabel: { color: '#606266' },
      splitLine: { lineStyle: { color: '#ebeef5' } }
    },
    dataZoom: [{ type: 'inside', start: 0, end: 100 }],
    series: [{
      name: '人流量',
      type: 'line',
      data: points,
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      showSymbol: false,
      lineStyle: { width: 2, color: '#67c23a' },
      itemStyle: { color: '#67c23a' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
          { offset: 1, color: 'rgba(103, 194, 58, 0.02)' }
        ])
      }
    }]
  }

  chartInstance.setOption(option, true)
  nextTick(() => setTimeout(() => chartInstance?.resize(), 50))
}

function onRangeChange() {
  if (rangeType.value !== 'custom') onSearch()
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.lightId = undefined
  query.pageNum = 1
  rangeType.value = '24h'
  startTime.value = ''
  endTime.value = ''
  onSearch()
}

function handleResize() { nextTick(() => chartInstance?.resize()) }

function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  timer = setInterval(() => loadData(), interval.value * 1000)
}

function stopPolling() {
  if (timer) { clearInterval(timer); timer = null }
}

watch(autoRefresh, (v) => { if (v) startPolling(); else stopPolling() })
watch(interval, () => { if (autoRefresh.value) startPolling() })

onMounted(async () => {
  await loadLights()
  await nextTick()
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value)
    window.addEventListener('resize', handleResize)
  }
  await loadData()
  setTimeout(() => chartInstance?.resize(), 200)
  startPolling()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
  stopPolling()
})
</script>

<style scoped>
.time-separator { margin: 0 8px; color: #909399; font-size: 14px; }
.stat-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card { background: #fff; border-radius: 8px; padding: 20px 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stat-value { display: flex; align-items: baseline; gap: 6px; }
.stat-num { font-size: 28px; font-weight: 600; color: #303133; line-height: 1.2; }
.stat-unit { font-size: 14px; color: #909399; }
.chart-card { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); position: relative; margin-bottom: 16px; }
.chart-container { width: 100%; height: 400px; }
.empty-tip { position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%); color: #909399; font-size: 14px; }
.table-card { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.table-wrapper { overflow-x: auto; -webkit-overflow-scrolling: touch; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
.flow-value { font-weight: 600; color: #67c23a; }
@media (max-width: 768px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .filter-bar { flex-wrap: wrap; gap: 8px; }
  .table-wrapper { overflow-x: auto; }
}
</style>