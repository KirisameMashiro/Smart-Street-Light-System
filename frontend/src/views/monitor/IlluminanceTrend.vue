<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">历史光照趋势</h2>
      <div class="toolbar">
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
      </template>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">最高光照</div>
        <div class="stat-value">
          <span class="stat-num">{{ stats.max.toFixed(0) }}</span>
          <span class="stat-unit">lux</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">最低光照</div>
        <div class="stat-value">
          <span class="stat-num">{{ stats.min.toFixed(0) }}</span>
          <span class="stat-unit">lux</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均光照</div>
        <div class="stat-value">
          <span class="stat-num">{{ stats.avg.toFixed(0) }}</span>
          <span class="stat-unit">lux</span>
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
      <div v-if="!loading && chartData.length === 0" class="empty-tip">
        暂无数据
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'IlluminanceTrend' })
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getSensorDataPage } from '@/api/sensor'
import { getAllLights } from '@/api/light'

const loading = ref(false)
const chartRef = ref(null)
let chartInstance = null

const lightOptions = ref([])
const rangeType = ref('24h')
const startTime = ref('')
const endTime = ref('')
const chartData = ref([])

const query = reactive({
  lightId: undefined
})

const stats = computed(() => {
  const data = chartData.value
  if (!data.length) {
    return { max: 0, min: 0, avg: 0, count: 0 }
  }
  let max = -Infinity
  let min = Infinity
  let sum = 0
  for (const d of data) {
    const v = d.illuminance ?? 0
    if (v > max) max = v
    if (v < min) min = v
    sum += v
  }
  return {
    max,
    min,
    avg: sum / data.length,
    count: data.length
  }
})

function getTimeRange() {
  const now = new Date()
  let start
  switch (rangeType.value) {
    case '1h':
      start = new Date(now.getTime() - 60 * 60 * 1000)
      break
    case '6h':
      start = new Date(now.getTime() - 6 * 60 * 60 * 1000)
      break
    case '24h':
      start = new Date(now.getTime() - 24 * 60 * 60 * 1000)
      break
    case '7d':
      start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      break
    case 'custom':
    default:
      return {
        start: startTime.value || undefined,
        end: endTime.value || undefined
      }
  }
  return {
    start: start.toISOString().slice(0, 19).replace('T', ' '),
    end: now.toISOString().slice(0, 19).replace('T', ' ')
  }
}

async function loadLights() {
  try {
    const res = await getAllLights()
    lightOptions.value = res.data || []
  } catch (e) {
    // ignore
  }
}

async function loadData() {
  loading.value = true
  try {
    const range = getTimeRange()
    const params = {
      pageNum: 1,
      pageSize: 2000,
      lightId: query.lightId,
      startTime: range.start,
      endTime: range.end
    }
    const res = await getSensorDataPage(params)
    const records = res.data?.records || []
    chartData.value = records
      .map((r) => ({
        time: r.collectTime,
        illuminance: r.illuminance ?? 0
      }))
      .sort((a, b) => new Date(a.time) - new Date(b.time))
    renderChart()
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartInstance || !chartRef.value) return
  const times = chartData.value.map((d) => d.time)
  const values = chartData.value.map((d) => d.illuminance)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.axisValue}<br/>光照: <strong>${p.value}</strong> lux`
      }
    },
    grid: {
      left: 60,
      right: 30,
      top: 30,
      bottom: 60
    },
    xAxis: {
      type: 'category',
      data: times,
      boundaryGap: false,
      axisLabel: {
        rotate: 0,
        formatter: (value) => {
          if (!value) return value
          const t = typeof value === 'string' ? value.replace('T', ' ') : value
          return t.slice(5, 16)
        }
      },
      axisLine: { lineStyle: { color: '#dcdfe6' } }
    },
    yAxis: {
      type: 'value',
      name: '光照 (lux)',
      nameTextStyle: { color: '#909399' },
      axisLabel: { color: '#606266' },
      splitLine: { lineStyle: { color: '#ebeef5' } }
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        type: 'slider',
        start: 0,
        end: 100,
        height: 20,
        bottom: 10
      }
    ],
    series: [
      {
        name: '光照强度',
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        showSymbol: false,
        lineStyle: {
          width: 2,
          color: '#409eff'
        },
        itemStyle: {
          color: '#409eff'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.02)' }
          ])
        }
      }
    ]
  }

  chartInstance.setOption(option, true)
}

function onRangeChange() {
  if (rangeType.value !== 'custom') {
    onSearch()
  }
}

function onSearch() {
  loadData()
}

function onReset() {
  query.lightId = undefined
  rangeType.value = '24h'
  startTime.value = ''
  endTime.value = ''
  onSearch()
}

function handleResize() {
  chartInstance?.resize()
}

onMounted(async () => {
  await loadLights()
  await nextTick()
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value)
    window.addEventListener('resize', handleResize)
  }
  loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.time-separator {
  margin: 0 8px;
  color: #909399;
  font-size: 14px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.stat-num {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.stat-unit {
  font-size: 14px;
  color: #909399;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  position: relative;
}

.chart-container {
  width: 100%;
  height: 480px;
}

.empty-tip {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #909399;
  font-size: 14px;
}

@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
