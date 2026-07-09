<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">控制台</h2>
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
        <el-button :icon="Refresh" @click="loadAll" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <StatCard
        label="路灯总数"
        :value="stats.total"
        unit=" 盏"
        icon="Sunny"
        color="#409eff"
        desc="全部在册设备"
      />
      <StatCard
        label="在线开启"
        :value="stats.online"
        unit=" 盏"
        icon="Lightning"
        color="#67c23a"
        :desc="`占比 ${onlineRate}%`"
      />
      <StatCard
        label="关闭离线"
        :value="stats.offline"
        unit=" 盏"
        icon="TurnOff"
        color="#909399"
      />
      <StatCard
        label="设备故障"
        :value="stats.fault"
        unit=" 盏"
        icon="WarningFilled"
        color="#f56c6c"
        :desc="stats.fault > 0 ? '需及时处理' : '运行良好'"
      />
      <StatCard
        label="未处理报警"
        :value="unhandled"
        unit=" 条"
        icon="BellFilled"
        color="#e6a23c"
      />
    </div>

    <!-- 图表区 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="chart-card">
          <div class="chart-title">路灯状态分布</div>
          <div ref="pieRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="chart-card">
          <div class="chart-title">设备类型分布</div>
          <div ref="barRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 各行政区设备分布 -->
    <div class="chart-card" style="margin-top: 16px">
      <div class="chart-title">各行政区设备分布</div>
      <el-empty
        v-if="districtError || districtData.length === 0"
        description="后端接口缺失或暂无数据"
        :image-size="80"
      />
      <div v-else ref="districtRef" class="chart-box"></div>
    </div>

    <!-- 最近报警 -->
    <div class="chart-card" style="margin-top: 16px">
      <div class="chart-title" style="display:flex;justify-content:space-between;align-items:center">
        <span>最近报警</span>
        <el-button link type="primary" @click="$router.push('/alerts')">查看全部</el-button>
      </div>
      <el-table :data="recentAlerts" stripe size="default">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="报警类型" width="120">
          <template #default="{ row }">
            {{ ALERT_TYPE_MAP[row.alertType] || '其他' }}
          </template>
        </el-table-column>
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="ALERT_LEVEL_MAP[row.alertLevel]?.type" size="small">
              {{ ALERT_LEVEL_MAP[row.alertLevel]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="报警内容" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报警时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'Dashboard' })
import { ref, reactive, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import StatCard from '@/components/StatCard.vue'
import { getLightStats, getAllLights, getLightGroupStats } from '@/api/light'
import { getAlertPage, getUnhandledCount } from '@/api/alert'
import {
  ALERT_TYPE_MAP,
  ALERT_LEVEL_MAP,
  STATUS_COLORS
} from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import { useAppStore } from '@/store/app'

const appStore = useAppStore()

const loading = ref(false)
const autoRefresh = ref(false)
const interval = ref(5)
const stats = reactive({ total: 0, online: 0, offline: 0, fault: 0 })
const unhandled = ref(0)
const recentAlerts = ref([])
const allLights = ref([])
const districtData = ref([])
const districtError = ref(false)

const onlineRate = computed(() =>
  stats.total ? ((stats.online / stats.total) * 100).toFixed(1) : '0.0'
)

const pieRef = ref()
const barRef = ref()
const districtRef = ref()
let pieChart = null
let barChart = null
let districtChart = null

async function loadStats() {
  try {
    const res = await getLightStats()
    Object.assign(stats, res.data || {})
  } catch (e) {
    // 拦截器已提示
  }
}

async function loadUnhandled() {
  try {
    const res = await getUnhandledCount()
    unhandled.value = res.data || 0
  } catch (e) {
    // 拦截器已提示
  }
}

async function loadRecentAlerts() {
  try {
    const res = await getAlertPage({ pageNum: 1, pageSize: 5, status: 0 })
    recentAlerts.value = res.data?.records || []
  } catch (e) {
    recentAlerts.value = []
  }
}

async function loadAllLights() {
  try {
    const res = await getAllLights()
    allLights.value = res.data || []
  } catch (e) {
    allLights.value = []
  }
}

// 归一化分组统计返回结构（兼容 name/groupKey/district 与 count/total/value）
function normalizeGroup(item) {
  return {
    name: item.name || item.groupKey || item.district || item.key || '未知',
    value: Number(item.count ?? item.total ?? item.value ?? 0)
  }
}

async function loadDistrict() {
  try {
    const res = await getLightGroupStats('district')
    const arr = Array.isArray(res.data) ? res.data : []
    districtData.value = arr.map(normalizeGroup)
    districtError.value = false
  } catch (e) {
    // 后端 group-stats 接口缺失：占位显示
    districtData.value = []
    districtError.value = true
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([
      loadStats(),
      loadUnhandled(),
      loadRecentAlerts(),
      loadAllLights(),
      loadDistrict()
    ])
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  // 状态分布饼图（LIGHT_STATUS 着色）
  if (pieRef.value) {
    pieChart = pieChart || echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      color: [STATUS_COLORS.on, STATUS_COLORS.off, STATUS_COLORS.fault],
      series: [
        {
          name: '路灯状态',
          type: 'pie',
          radius: ['45%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}\n{c}' },
          data: [
            { value: stats.online, name: '开启' },
            { value: stats.offline, name: '关闭' },
            { value: stats.fault, name: '故障' }
          ]
        }
      ]
    })
  }

  // 设备类型分布柱状图（前端聚合）
  if (barRef.value) {
    barChart = barChart || echarts.init(barRef.value)
    const typeMap = {}
    allLights.value.forEach((l) => {
      const t = l.deviceType || '未知'
      typeMap[t] = (typeMap[t] || 0) + 1
    })
    const names = Object.keys(typeMap)
    const values = names.map((n) => typeMap[n])
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: {
        type: 'category',
        data: names,
        axisLabel: { interval: 0, rotate: names.length > 4 ? 20 : 0 }
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          type: 'bar',
          data: values,
          barMaxWidth: 40,
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#409eff' },
              { offset: 1, color: '#79bbff' }
            ])
          }
        }
      ]
    })
  }

  // 各行政区设备分布柱状图
  if (districtRef.value && districtData.value.length > 0) {
    districtChart = districtChart || echarts.init(districtRef.value)
    const names = districtData.value.map((d) => d.name)
    const values = districtData.value.map((d) => d.value)
    districtChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
      xAxis: {
        type: 'category',
        data: names,
        axisLabel: { interval: 0, rotate: names.length > 5 ? 20 : 0 }
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          type: 'bar',
          data: values,
          barMaxWidth: 48,
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#67c23a' },
              { offset: 1, color: '#95d475' }
            ])
          }
        }
      ]
    })
  }
}

function onResize() {
  pieChart?.resize()
  barChart?.resize()
  districtChart?.resize()
}

let timer = null
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

watch(() => appStore.lightDataVersion, () => {
  loadAll()
})

onMounted(() => {
  loadAll()
  startPolling()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  barChart?.dispose()
  districtChart?.dispose()
})
</script>

<style scoped>
.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--card-shadow);
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.chart-box {
  height: 300px;
  width: 100%;
}

@media (max-width: 768px) {
  .chart-box {
    height: 220px;
  }

  .chart-card {
    padding: 12px;
    margin-bottom: 8px;
  }

  .chart-title {
    font-size: 14px;
    margin-bottom: 8px;
  }
}
</style>
