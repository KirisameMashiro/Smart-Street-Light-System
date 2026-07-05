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
        <el-radio-button value="month">月度</el-radio-button>
        <el-radio-button value="year">年度</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="rangeType === 'month'"
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
      <span class="text-muted">统计口径：相比传统钠灯基准能耗</span>
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
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="chart-card">
          <div class="chart-title">{{ rangeType === 'month' ? '月度' : '年度' }}趋势</div>
          <el-empty v-if="trendError" description="趋势数据暂不可用（后端接口缺失）" :image-size="80" />
          <div v-else ref="trendRef" class="chart-box" v-loading="loading"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="chart-card">
          <div class="chart-title">路段节电量对比</div>
          <el-empty v-if="roadError" description="路段对比数据暂不可用（后端接口缺失）" :image-size="80" />
          <div v-else ref="roadRef" class="chart-box" v-loading="loading"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 能耗基准配置 -->
    <div class="chart-card" style="margin-top: 16px">
      <div class="chart-title">能耗基准配置（传统钠灯）</div>
      <el-form
        ref="baselineFormRef"
        :model="baseline"
        v-loading="baselineLoading"
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
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
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
  exportCarbonReport
} from '@/api/carbon'

const loading = ref(false)
const exporting = ref(false)
const rangeType = ref('month')
const dateValue = ref('')

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
  return rangeType.value === 'month'
    ? `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
    : `${d.getFullYear()}`
}

function onRangeTypeChange() {
  dateValue.value = defaultPeriod()
  loadAll()
}

async function loadSummary() {
  try {
    const res = await getCarbonSummary({ type: rangeType.value, period: dateValue.value })
    const d = res.data || {}
    summary.savedEnergy = Number(d.savedEnergy ?? 0)
    summary.reducedCo2 = Number(d.reducedCo2 ?? 0)
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
    const res = await getCarbonTrend({ type: rangeType.value, period: dateValue.value })
    trendData.value = Array.isArray(res.data) ? res.data : res.data?.list || []
  } catch (e) {
    trendData.value = []
    trendError.value = true
  }
}

async function loadRoad() {
  roadError.value = false
  try {
    const res = await getCarbonRoadCompare({ type: rangeType.value, period: dateValue.value })
    roadData.value = Array.isArray(res.data) ? res.data : res.data?.list || []
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
    renderTrend()
    renderRoad()
  } finally {
    loading.value = false
  }
}

function renderTrend() {
  if (!trendRef.value || trendError.value) return
  trendChart = trendChart || echarts.init(trendRef.value)
  const x = trendData.value.map((it) => it.period || it.date || it.month || '')
  const energy = trendData.value.map((it) => Number(it.savedEnergy ?? 0))
  const co2 = trendData.value.map((it) => Number(it.reducedCo2 ?? 0))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['节电量(kWh)', '减排量(kgCO₂)'], bottom: 0 },
    grid: { left: 55, right: 60, top: 30, bottom: 40 },
    xAxis: { type: 'category', boundaryGap: false, data: x },
    yAxis: [
      { type: 'value', name: '节电量' },
      { type: 'value', name: '减排量' }
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
  })
}

function renderRoad() {
  if (!roadRef.value || roadError.value) return
  roadChart = roadChart || echarts.init(roadRef.value)
  const names = roadData.value.map((it) => it.road || it.name || '')
  const values = roadData.value.map((it) => Number(it.savedEnergy ?? 0))
  roadChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 55, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { interval: 0, rotate: names.length > 4 ? 20 : 0 }
    },
    yAxis: { type: 'value', name: '节电量(kWh)' },
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
  })
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
    await updateEnergyBaseline({ ...baseline })
    ElMessage.success('基准配置已保存')
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
    ['统计类型', rangeType.value, ''],
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

.form-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
