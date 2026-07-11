<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">碳减排分析</h2>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="success" :icon="Download" :loading="exporting" @click="onExport">
          导出 Excel
        </el-button>
      </div>
    </div>

    <!-- 时间范围选择 -->
    <div class="filter-bar">
      <el-radio-group v-model="rangeType" @change="onRangeTypeChange">
        <el-radio-button value="monthly">月度</el-radio-button>
        <el-radio-button value="yearly">年度</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="rangeType === 'monthly'"
        v-model="dateValue"
        type="month"
        placeholder="选择月份"
        value-format="YYYY-MM"
        style="width: 180px"
        @change="loadAll"
      />
      <el-date-picker
        v-else
        v-model="dateValue"
        type="year"
        placeholder="选择年份"
        value-format="YYYY"
        style="width: 180px"
        @change="loadAll"
      />
      <span class="text-muted">
        统计口径：相比传统钠灯基准能耗
        <template v-if="rangeType === 'monthly'">（月度统计展示该月每日数据）</template>
        <template v-else>（年度统计展示该年每月数据）</template>
      </span>
    </div>

    <!-- 核心指标卡片 -->
    <div class="stat-grid">
      <StatCard
        label="总节电量"
        :value="summary.savedEnergy"
        unit=" kWh"
        icon="Lightning"
        color="#67c23a"
        :desc="summaryError ? '数据暂不可用' : '相比传统钠灯基准'"
      />
      <StatCard
        label="总减排量"
        :value="summary.reducedCo2"
        unit=" kgCO₂"
        icon="WindPower"
        color="#409eff"
        :desc="summaryError ? '数据暂不可用' : '按区域碳排放因子换算'"
      />
      <StatCard
        label="节能率"
        :value="summary.energySavingRate"
        unit=" %"
        icon="TrendCharts"
        color="#e6a23c"
        :desc="summaryError ? '数据暂不可用' : '相对基准能耗'"
      />
    </div>

    <!-- 图表区 -->
    <div class="chart-grid">
      <div class="chart-card">
        <div class="chart-title">{{ trendLabel }}趋势</div>
        <el-empty v-show="trendError || (!trendData.length && !loading)" description="趋势数据暂不可用（后端接口缺失）" :image-size="80" />
        <div ref="trendRef" class="chart-box"></div>
      </div>
      <div class="chart-card">
        <div class="chart-title">{{ rangeType === 'monthly' ? '月度' : '年度' }}路段节电量对比 <span class="chart-subtitle">{{ dateValue }}</span></div>
        <el-empty v-show="roadError || (!roadData.length && !loading)" description="路段对比数据暂不可用（后端接口缺失）" :image-size="80" />
        <div ref="roadRef" class="chart-box"></div>
      </div>
    </div>

    <!-- 能耗基准配置 -->
    <div class="chart-card" style="margin-top: 16px">
      <div class="chart-title">能耗基准配置（传统钠灯）</div>
      <el-form
        ref="baselineFormRef"
        :model="baseline"
        label-width="200px"
        style="max-width: 600px"
      >
        <el-form-item label="基准功率 (W)">
          <el-input-number
            v-model="baseline.basePower"
            :min="0"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="日均亮灯时长 (h)">
          <el-input-number
            v-model="baseline.dailyHours"
            :min="0"
            :max="24"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="区域碳排放因子">
          <el-input-number
            v-model="baseline.emissionFactor"
            :min="0"
            :step="0.01"
            :precision="4"
            style="width: 100%"
          />
          <span class="form-hint">单位 kgCO₂/kWh</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="baselineSaving" @click="onSaveBaseline">
            保存配置
          </el-button>
          <el-button @click="loadBaseline">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'CarbonAnalysis' })
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import * as XLSX from 'xlsx'
import StatCard from '@/components/StatCard.vue'
import {
  getCarbonSummary,
  getCarbonTrend,
  getCarbonRoadCompare,
  getEnergyBaseline,
  updateEnergyBaseline,
  exportCarbonReport,
  recomputeCarbonStats
} from '@/api/carbon'

const loading = ref(false)
const exporting = ref(false)
const rangeType = ref('monthly')
const dateValue = ref('')

const trendLabel = computed(() => {
  const map = { monthly: '月度每日', yearly: '年度每月' }
  return map[rangeType.value] || '月度每日'
})

const summary = reactive({
  savedEnergy: 0,
  reducedCo2: 0,
  energySavingRate: 0
})
const summaryError = ref(false)

const trendRef = ref()
const roadRef = ref()
let trendChart = null
let roadChart = null

const trendData = ref([])
const roadData = ref([])
const trendError = ref(false)
const roadError = ref(false)

// 能耗基准
const baselineLoading = ref(false)
const baselineSaving = ref(false)
const baseline = reactive({
  basePower: 250,
  dailyHours: 11,
  emissionFactor: 0.581
})

function defaultPeriod() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  if (rangeType.value === 'monthly') {
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
  } else {
    return `${d.getFullYear()}`
  }
}

function onRangeTypeChange() {
  dateValue.value = defaultPeriod()
  loadAll()
}

async function loadSummary() {
  try {
    const res = await getCarbonSummary({ type: rangeType.value, period: dateValue.value })
    const d = res.data || {}
    summary.savedEnergy = Number(d.totalSavedPower ?? d.savedEnergy ?? 0)
    summary.reducedCo2 = Number(d.totalReducedCO2 ?? d.reducedCo2 ?? 0)
    summary.energySavingRate = Number(d.energySavingRate ?? 0)
    summaryError.value = false
  } catch (e) {
    summary.savedEnergy = 0
    summary.reducedCo2 = 0
    summary.energySavingRate = 0
    summaryError.value = true
  }
}

async function loadTrend() {
  trendError.value = false
  try {
    // 后端期望 type=month/year，前端使用 monthly/yearly
    const backendType = rangeType.value === 'monthly' ? 'month' : 'year'
    const res = await getCarbonTrend({ type: backendType, period: dateValue.value })
    trendData.value = Array.isArray(res.data) ? res.data : res.data?.list || []
    if (!trendData.value.length) {
      trendError.value = true
    }
  } catch (e) {
    trendData.value = []
    trendError.value = true
  }
}

async function loadRoad() {
  roadError.value = false
  try {
    const backendType = rangeType.value === 'monthly' ? 'month' : 'year'
    const res = await getCarbonRoadCompare({ type: backendType, period: dateValue.value })
    roadData.value = Array.isArray(res.data) ? res.data : res.data?.list || []
    if (!roadData.value.length) {
      roadError.value = true
    }
  } catch (e) {
    roadData.value = []
    roadError.value = true
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadSummary(), loadTrend(), loadRoad()])
    await nextTick()
    setTimeout(() => {
      renderTrend()
      renderRoad()
    }, 100)
  } finally {
    loading.value = false
  }
}

function renderTrend() {
  if (!trendRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendRef.value)
  }
  if (trendError.value) {
    trendChart.setOption({
      tooltip: {},
      legend: { show: false },
      grid: { left: 0, right: 0, top: 0, bottom: 0 },
      xAxis: { type: 'category', data: [], show: false },
      yAxis: { type: 'value', show: false },
      series: []
    }, true)
    return
  }
  
  const isMonthly = rangeType.value === 'monthly'
  
  const x = trendData.value.map((it) => {
    if (it.period) return it.period
    if (it.date) return it.date
    if (it.month) return it.month
    if (it.statDate) return it.statDate
    return ''
  })
  const energy = trendData.value.map((it) => Number(it.savedEnergy ?? 0))
  const co2 = trendData.value.map((it) => Number(it.reducedCo2 ?? it.co2Reduction ?? 0))
  
  const unit = isMonthly ? '日' : '月'
  
  trendChart.setOption({
    tooltip: { 
      trigger: 'axis',
      formatter: function(params) {
        const dateLabel = params[0].axisValue
        let result = `<div style="font-weight:600;margin-bottom:4px">${dateLabel}</div>`
        params.forEach(item => {
          const color = item.color
          const name = item.seriesName
          const value = item.value
          result += `<div style="display:flex;align-items:center;gap:6px;margin:4px 0">
            <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${color}"></span>
            <span>${name}: <strong>${value}</strong></span>
          </div>`
        })
        return result
      }
    },
    legend: { data: ['节电量(kWh)', '减排量(kgCO₂)'], bottom: 0 },
    grid: { left: 55, right: 65, top: 45, bottom: 60, containLabel: true },
    xAxis: { 
      type: 'category', 
      boundaryGap: false, 
      data: x, 
      axisLabel: { 
        fontSize: 11,
        rotate: isMonthly && x.length > 15 ? 45 : 0,
        formatter: function(value) {
          if (isMonthly) {
            if (value.length === 10) return value.slice(8) + '日'
            if (value.length === 7) return value.slice(5) + '月'
            return value
          } else {
            if (value.length === 7) return value.slice(5) + '月'
            return value
          }
        }
      } 
    },
    yAxis: [
      { type: 'value', name: '节电量(kWh)', nameGap: 20 },
      { type: 'value', name: '减排量(kgCO₂)', nameGap: 20 }
    ],
    series: [
      {
        name: '节电量(kWh)',
        type: 'line',
        smooth: true,
        data: energy,
        itemStyle: { color: '#67c23a' },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '减排量(kgCO₂)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: co2,
        itemStyle: { color: '#409eff' }
      }
    ]
  }, true)
}

function renderRoad() {
  if (!roadRef.value) return
  if (!roadChart) {
    roadChart = echarts.init(roadRef.value)
  }
  if (roadError.value) {
    roadChart.setOption({
      tooltip: {},
      grid: { left: 0, right: 0, top: 0, bottom: 0 },
      xAxis: { type: 'category', data: [], show: false },
      yAxis: { type: 'value', show: false },
      series: []
    }, true)
    return
  }
  const names = roadData.value.map((it) => it.road || it.name || '')
  const values = roadData.value.map((it) => {
    if (it.savedEnergy !== undefined) return Number(it.savedEnergy)
    if (it.before !== undefined && it.after !== undefined) return Number(it.before) - Number(it.after)
    return 0
  })
  roadChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 55, right: 25, top: 45, bottom: 60, containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { interval: 0, rotate: names.length > 4 ? 20 : 0, fontSize: 11 }
    },
    yAxis: { type: 'value', name: '节电量(kWh)', nameGap: 15 },
    series: [
      {
        type: 'bar',
        data: values,
        barMaxWidth: 40,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#67c23a' },
            { offset: 1, color: '#b3e19d' }
          ])
        }
      }
    ]
  }, true)
}

async function loadBaseline() {
  baselineLoading.value = true
  try {
    const res = await getEnergyBaseline()
    const d = res.data || {}
    baseline.basePower = d.basePower ?? 250
    baseline.dailyHours = d.dailyHours ?? 11
    baseline.emissionFactor = d.emissionFactor ?? 0.581
  } catch (e) {
    // 后端缺失：保留默认值，错误已由拦截器提示
  } finally {
    baselineLoading.value = false
  }
}

async function onSaveBaseline() {
  baselineSaving.value = true
  try {
    // 1. 保存基准配置到后端
    await updateEnergyBaseline({ ...baseline })
    ElMessage.success('基准配置已保存')

    // 2. 触发后端全量重算碳减排统计（使用新基准重新计算所有历史数据）
    try {
      const res = await recomputeCarbonStats()
      ElMessage.success(res?.data?.message || '碳减排统计已重新计算')
    } catch (e) {
      console.warn('碳减排统计重算失败（可能无传感器数据）', e)
    }

    // 3. 重新加载核心指标、趋势图、路段对比图
    await loadAll()
  } catch (e) {
    // 失败：错误已由拦截器提示
  } finally {
    baselineSaving.value = false
  }
}

// ====== Excel 导出 ======
function timestamp() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

function exportLocal() {
  const wb = XLSX.utils.book_new()
  // 核心指标
  const summaryAoA = [
    ['指标', '数值', '单位'],
    ['总节电量', summary.savedEnergy, 'kWh'],
    ['总减排量', summary.reducedCo2, 'kgCO2'],
    ['节能率', summary.energySavingRate, '%'],
    ['统计类型', rangeType.value === 'monthly' ? '月度(每日)' : '年度(每月)', ''],
    ['统计周期', dateValue.value || '', '']
  ]
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(summaryAoA), '核心指标')
  // 趋势
  const trendAoA = [
    ['时间', '节电量(kWh)', '减排量(kgCO2)'],
    ...trendData.value.map((it) => [
      it.period || it.date || it.month || '',
      it.savedEnergy ?? '',
      it.reducedCo2 ?? ''
    ])
  ]
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(trendAoA), '趋势')
  // 路段对比
  const roadAoA = [
    ['路段', '节电量(kWh)'],
    ...roadData.value.map((it) => [it.road || it.name || '', it.savedEnergy ?? ''])
  ]
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(roadAoA), '路段对比')
  // 基准配置
  const baseAoA = [
    ['参数', '值', '单位'],
    ['传统钠灯基准功率', baseline.basePower, 'W'],
    ['日均亮灯时长', baseline.dailyHours, 'h'],
    ['区域碳排放因子', baseline.emissionFactor, 'kgCO2/kWh']
  ]
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(baseAoA), '基准配置')
  XLSX.writeFile(wb, `carbon_report_${timestamp()}.xlsx`)
}

async function onExport() {
  exporting.value = true
  try {
    // 优先调用后端导出
    const blob = await exportCarbonReport({ type: rangeType.value, period: dateValue.value })
    if (blob instanceof Blob) {
      downloadBlob(blob, `carbon_report_${timestamp()}.xlsx`)
      ElMessage.success('报表导出成功')
    } else {
      // 非预期返回，走本地兜底
      exportLocal()
      ElMessage.success('已使用本地数据生成报表')
    }
  } catch (e) {
    // 后端导出接口缺失：本地兜底生成
    try {
      exportLocal()
      ElMessage.success('后端导出不可用，已使用本地数据生成报表')
    } catch (err) {
      ElMessage.error('导出失败：' + (err?.message || '未知错误'))
    }
  } finally {
    exporting.value = false
  }
}

function onResize() {
  trendChart?.resize()
  roadChart?.resize()
}

onMounted(() => {
  dateValue.value = defaultPeriod()
  loadAll()
  loadBaseline()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  roadChart?.dispose()
})
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  padding-bottom: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.toolbar {
  display: flex;
  gap: 8px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: var(--card-shadow);
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--card-shadow);
  min-width: 0;
  min-height: 380px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.chart-subtitle {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
  margin-left: 8px;
}

.chart-box {
  height: 300px;
  width: 100%;
}

.form-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }

  .chart-card {
    min-height: auto;
  }

  .chart-box {
    height: 250px;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
