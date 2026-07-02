<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">控制台</h2>
      <el-button :icon="Refresh" @click="loadAll" :loading="loading">刷新</el-button>
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

    <!-- 最近报警 -->
    <div class="chart-card" style="margin-top: 16px">
      <div class="chart-title" style="display:flex;justify-content:space-between;align-items:center">
        <span>最近报警</span>
        <el-button link type="primary" @click="$router.push('/alerts')">查看全部</el-button>
      </div>
      <el-table :data="recentAlerts" v-loading="loading" stripe size="default">
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
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import StatCard from '@/components/StatCard.vue'
import { getLightStats, getAllLights } from '@/api/light'
import { getAlertPage, getUnhandledCount } from '@/api/alert'
import { ALERT_TYPE_MAP, ALERT_LEVEL_MAP } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const stats = reactive({ total: 0, online: 0, offline: 0, fault: 0 })
const unhandled = ref(0)
const recentAlerts = ref([])

const onlineRate = computed(() =>
  stats.total ? ((stats.online / stats.total) * 100).toFixed(1) : '0.0'
)

const pieRef = ref()
const barRef = ref()
let pieChart = null
let barChart = null

async function loadStats() {
  const res = await getLightStats()
  Object.assign(stats, res.data || {})
}

async function loadUnhandled() {
  const res = await getUnhandledCount()
  unhandled.value = res.data || 0
}

async function loadRecentAlerts() {
  const res = await getAlertPage({ pageNum: 1, pageSize: 5, status: 0 })
  recentAlerts.value = res.data?.records || []
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadUnhandled(), loadRecentAlerts()])
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  // 状态分布饼图
  if (pieRef.value) {
    pieChart = pieChart || echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      color: ['#67c23a', '#909399', '#f56c6c'],
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

  // 设备类型分布柱状图
  if (barRef.value) {
    barChart = barChart || echarts.init(barRef.value)
    getAllLights().then((res) => {
      const all = res.data || []
      const typeMap = {}
      all.forEach((l) => {
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
    })
  }
}

function onResize() {
  pieChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  barChart?.dispose()
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
</style>
