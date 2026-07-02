<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">台账统计</h2>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
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
    </div>

    <!-- 分组统计柱状图 -->
    <div class="chart-card">
      <div
        class="chart-title"
        style="display:flex;justify-content:space-between;align-items:center"
      >
        <span>分组统计</span>
        <el-radio-group v-model="groupBy" size="small" @change="loadGroupStats">
          <el-radio-button
            v-for="o in GROUP_BY_OPTIONS"
            :key="o.value"
            :value="o.value"
          >{{ o.label }}</el-radio-button>
        </el-radio-group>
      </div>
      <div
        v-loading="groupLoading"
        class="chart-box"
        style="display:flex;align-items:center;justify-content:center"
      >
        <el-empty
          v-if="groupError"
          :description="groupError"
          :image-size="80"
        />
        <div v-else ref="groupRef" class="chart-box-inner"></div>
      </div>
    </div>

    <!-- 设备类型分布 & 状态占比 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="12">
        <div class="chart-card">
          <div class="chart-title">设备类型分布</div>
          <div ref="typeRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="chart-card">
          <div class="chart-title">各状态占比</div>
          <div ref="statusRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import StatCard from '@/components/StatCard.vue'
import {
  getLightStats,
  getLightGroupStats,
  getAllLights
} from '@/api/light'
import { GROUP_BY_OPTIONS, LIGHT_STATUS_MAP } from '@/utils/constants'

const loading = ref(false)
const stats = reactive({ total: 0, online: 0, offline: 0, fault: 0 })

const onlineRate = computed(() =>
  stats.total ? ((stats.online / stats.total) * 100).toFixed(1) : '0.0'
)

const groupBy = ref('district')
const groupLoading = ref(false)
const groupError = ref('')

const groupRef = ref()
const typeRef = ref()
const statusRef = ref()
let groupChart = null
let typeChart = null
let statusChart = null

// 状态统计
async function loadStats() {
  try {
    const res = await getLightStats()
    Object.assign(stats, res.data || { total: 0, online: 0, offline: 0, fault: 0 })
  } catch (e) {
    // 接口缺失时清空，由拦截器提示
    Object.assign(stats, { total: 0, online: 0, offline: 0, fault: 0 })
  }
  await nextTick()
  renderStatus()
}

// 分组统计（后端缺失时显示占位）
async function loadGroupStats() {
  groupLoading.value = true
  groupError.value = ''
  try {
    const res = await getLightGroupStats(groupBy.value)
    const list = res.data || []
    await nextTick()
    renderGroup(list)
  } catch (e) {
    // 后端分组统计接口缺失：图表区显示错误占位，组件仍正常渲染
    groupError.value = '分组统计接口未实现或请求失败'
  } finally {
    groupLoading.value = false
  }
}

// 设备类型分布（前端聚合，不依赖缺失接口）
async function loadTypeDist() {
  let all = []
  try {
    const res = await getAllLights()
    all = res.data || []
  } catch (e) {
    all = []
  }
  await nextTick()
  renderType(all)
}

function renderGroup(list) {
  if (!groupRef.value) return
  groupChart = groupChart || echarts.init(groupRef.value)
  const names = list.map(
    (i) => i.name || i.groupName || i.district || i.road || i.deviceType || i.key || '未知'
  )
  const values = list.map((i) => Number(i.count ?? i.value ?? i.total ?? 0))
  groupChart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 40, right: 20, top: 20, bottom: 50 },
      xAxis: {
        type: 'category',
        data: names,
        axisLabel: { interval: 0, rotate: names.length > 4 ? 25 : 0 }
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '数量',
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
    },
    true
  )
}

function renderType(all) {
  if (!typeRef.value) return
  typeChart = typeChart || echarts.init(typeRef.value)
  const map = {}
  all.forEach((l) => {
    const t = l.deviceType || '未知'
    map[t] = (map[t] || 0) + 1
  })
  const data = Object.keys(map).map((n) => ({ name: n, value: map[n] }))
  typeChart.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          name: '设备类型',
          type: 'pie',
          radius: ['40%', '68%'],
          center: ['50%', '45%'],
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}\n{c}' },
          data:
            data.length > 0
              ? data
              : [{ name: '暂无数据', value: 0, itemStyle: { color: '#e4e7ed' } }]
        }
      ]
    },
    true
  )
}

function renderStatus() {
  if (!statusRef.value) return
  statusChart = statusChart || echarts.init(statusRef.value)
  statusChart.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      color: ['#67c23a', '#909399', '#f56c6c'],
      series: [
        {
          name: '路灯状态',
          type: 'pie',
          radius: ['45%', '70%'],
          center: ['50%', '45%'],
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}\n{c}' },
          data: [
            { value: stats.online, name: LIGHT_STATUS_MAP[1]?.label || '开启' },
            { value: stats.offline, name: LIGHT_STATUS_MAP[0]?.label || '关闭' },
            { value: stats.fault, name: LIGHT_STATUS_MAP[2]?.label || '故障' }
          ]
        }
      ]
    },
    true
  )
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadTypeDist(), loadGroupStats()])
    await nextTick()
    onResize()
  } finally {
    loading.value = false
  }
}

function onResize() {
  groupChart?.resize()
  typeChart?.resize()
  statusChart?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  groupChart?.dispose()
  typeChart?.dispose()
  statusChart?.dispose()
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
  height: 320px;
  width: 100%;
}

.chart-box-inner {
  height: 100%;
  width: 100%;
}
</style>
