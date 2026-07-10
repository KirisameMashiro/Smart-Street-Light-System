<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">预测调光</h2>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <!-- 顶部搜索 -->
    <div class="filter-bar">
      <el-autocomplete
        v-model="searchKeyword"
        :fetch-suggestions="fetchSuggestions"
        placeholder="输入编号/名称/位置/行政区搜索路灯"
        :trigger-on-focus="true"
        clearable
        style="width: 420px"
        @select="onSelectSuggestion"
        @clear="onClearSearch"
      >
        <template #default="{ item }">
          <div class="suggest-item">
            <span class="suggest-code">{{ item.lightCode }}</span>
            <span class="suggest-name">{{ item.lightName }}</span>
            <span class="suggest-meta">{{ item.district }} · {{ item.road }}</span>
          </div>
        </template>
      </el-autocomplete>
    </div>

    <!-- 算法说明 -->
    <div class="chart-card">
      <div class="chart-title">算法说明</div>
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          基于太阳几何模型（赤纬、时角、高度角）计算理论自然照度，叠加实时云量、降雨、雾况等天气衰减系数修正等效环境光照，再结合道路等级最低安全照度 Emin 换算出逐时推荐亮度，经三层边界强制校验（自然光充足节能关灯 / 不足时补至安全阈值 / 限制灯具最大输出）后输出最终调光建议。
        </template>
      </el-alert>
    </div>

    <!-- 预测结果图 -->
    <div class="chart-card" style="margin-top: 16px">
      <div class="chart-title">未来 24 小时推荐亮度</div>
      <el-empty
        v-if="!lightId"
        description="请先选择路灯"
        :image-size="80"
      />
      <el-empty
        v-else-if="resultError"
        description="预测结果暂不可用（后端接口缺失）"
        :image-size="80"
      />
      <div v-else ref="brightRef" class="chart-box"></div>
    </div>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="16">
        <div class="chart-card">
          <div class="chart-title">能耗效果对比</div>
          <el-empty
            v-if="!lightId"
            description="请先选择路灯"
            :image-size="80"
          />
          <el-empty
            v-else-if="compareError"
            description="对比数据暂不可用（后端接口缺失）"
            :image-size="80"
          />
          <div v-else ref="compareRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="chart-card accuracy-card">
          <div class="chart-title">模型置信度</div>
          <div class="accuracy-value">
            {{ compareError || !lightId ? '—' : accuracy + '%' }}
          </div>
          <div class="accuracy-source">
            <el-tag :type="accuracyTagType" size="small" effect="plain" round>
              {{ accuracyLabel }}
            </el-tag>
          </div>
          <div class="accuracy-detail">
            <div class="detail-title">置信度对照</div>
            <div class="detail-item" :class="{ active: accuracy >= 80 }">
              <span class="dot" style="background:#67c23a"></span>
              <span>真实天气 API 数据</span>
              <span class="detail-val">80-90%</span>
            </div>
            <div class="detail-item" :class="{ active: accuracy >= 70 && accuracy < 80 }">
              <span class="dot" style="background:#e6a23c"></span>
              <span>模拟天气数据</span>
              <span class="detail-val">70-79%</span>
            </div>
            <div class="detail-item" :class="{ active: accuracy < 70 }">
              <span class="dot" style="background:#f56c6c"></span>
              <span>无有效天气数据</span>
              <span class="detail-val">≤69%</span>
            </div>
          </div>
          <div v-if="savedEnergy" class="accuracy-extra">
            预测节能：{{ savedEnergy }} kWh
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
defineOptions({ name: 'AiPredict' })
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getPredictResult, getPredictCompare } from '@/api/predict'
import { getAllLights } from '@/api/light'

const loading = ref(false)
const lightId = ref(undefined)
const lightOptions = ref([])
const searchKeyword = ref('')

const brightRef = ref()
const compareRef = ref()
let brightChart = null
let compareChart = null

const resultData = ref([])
const compareData = ref(null)
const accuracy = ref(0)
const savedEnergy = ref(0)
const resultError = ref(false)
const compareError = ref(false)

// 最低安全照度对应亮度百分比（示例阈值）
const SAFETY_BRIGHTNESS = 30

// 置信度标签
const accuracyTagType = computed(() => {
  if (accuracy.value >= 80) return 'success'
  if (accuracy.value >= 70) return 'warning'
  return 'danger'
})
const accuracyLabel = computed(() => {
  if (accuracy.value >= 80) return '真实天气 API'
  if (accuracy.value >= 70) return '模拟天气数据'
  return '无有效天气数据'
})

const filteredLightOptions = computed(() => {
  const kw = searchKeyword.value?.trim().toLowerCase()
  if (!kw) return lightOptions.value
  return lightOptions.value.filter(l =>
    `${l.lightCode} ${l.lightName} ${l.location} ${l.district} ${l.road}`
      .toLowerCase().includes(kw)
  )
})

function fetchSuggestions(kw, cb) {
  const term = (kw || '').trim().toLowerCase()
  const source = lightOptions.value || []
  const results = term
    ? source.filter(l =>
        `${l.lightCode} ${l.lightName} ${l.location} ${l.district} ${l.road}`
          .toLowerCase().includes(term)
      )
    : source
  cb(results.slice(0, 50).map(l => ({
    ...l,
    value: `${l.lightCode} - ${l.lightName}`
  })))
}

function onSelectSuggestion(item) {
  searchKeyword.value = `${item.lightCode} - ${item.lightName}`
  lightId.value = item.id
  loadAll()
}

function onClearSearch() {
  lightId.value = undefined
  resultData.value = []
  compareData.value = null
}

async function loadLights() {
  try {
    const res = await getAllLights()
    lightOptions.value = res.data || []
  } catch (e) {
    lightOptions.value = []
  }
}

async function loadResult() {
  if (!lightId.value) {
    resultData.value = []
    resultError.value = false
    return
  }
  resultError.value = false
  try {
    const res = await getPredictResult(lightId.value)
    const d = res.data
    resultData.value = Array.isArray(d) ? d : d?.hours || d?.list || []
  } catch (e) {
    resultData.value = []
    resultError.value = true
  }
}

async function loadCompare() {
  if (!lightId.value) {
    compareData.value = null
    compareError.value = false
    accuracy.value = 0
    savedEnergy.value = 0
    return
  }
  compareError.value = false
  try {
    const res = await getPredictCompare({ lightId: lightId.value })
    compareData.value = res.data || null
    accuracy.value = Number(compareData.value?.accuracy ?? 0)
    savedEnergy.value = Number(compareData.value?.savedEnergy ?? 0)
  } catch (e) {
    compareData.value = null
    accuracy.value = 0
    savedEnergy.value = 0
    compareError.value = true
  }
}

async function loadAll() {
  if (!lightId.value) return
  loading.value = true
  try {
    await Promise.all([loadResult(), loadCompare()])
    await nextTick()
    setTimeout(() => {
      renderBright()
      renderCompare()
    }, 100)
  } finally {
    loading.value = false
  }
}

function renderBright() {
  if (!brightRef.value || resultError.value) return
  if (brightChart) {
    brightChart.dispose()
  }
  brightChart = echarts.init(brightRef.value)
  let hours = []
  let values = []
  if (Array.isArray(resultData.value) && resultData.value.length) {
    resultData.value.forEach((it, i) => {
      hours.push(it.hour ?? i)
      values.push(Number(it.brightness ?? 0))
    })
  } else {
    hours = Array.from({ length: 24 }, (_, i) => i)
    values = Array(24).fill(0)
  }
  brightChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (p) => `${p[0].axisValue} 时<br/>推荐亮度：${p[0].value}%`
    },
    grid: { left: 60, right: 100, top: 40, bottom: 50 },
    xAxis: { type: 'category', data: hours, name: '小时', nameTextStyle: { padding: [0, 0, 10, 0] } },
    yAxis: { type: 'value', name: '亮度(%)', min: 0, max: 100, nameTextStyle: { padding: [0, 20, 0, 0] } },
    series: [
      {
        name: '推荐亮度',
        type: 'line',
        smooth: true,
        data: values,
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.15 },
        markLine: {
          symbol: 'none',
          data: [{ yAxis: SAFETY_BRIGHTNESS, name: '最低安全照度' }],
          lineStyle: { color: '#f56c6c', type: 'dashed' },
          label: { formatter: '最低安全照度 {c}%', color: '#f56c6c' }
        }
      }
    ]
  })
}

function renderCompare() {
  if (!compareRef.value || compareError.value) return
  if (compareChart) {
    compareChart.dispose()
  }
  compareChart = echarts.init(compareRef.value)
  const d = compareData.value || {}
  const categories = ['预测模式', '固定阈值模式']
  const energy = [Number(d.predictEnergy ?? 0), Number(d.fixedEnergy ?? 0)]
  compareChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 60, right: 60, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: categories, nameTextStyle: { padding: [0, 0, 10, 0] } },
    yAxis: { type: 'value', name: '能耗(kWh)', nameTextStyle: { padding: [0, 20, 0, 0] } },
    series: [
      {
        name: '能耗',
        type: 'bar',
        data: [
          { value: energy[0], itemStyle: { color: '#67c23a' } },
          { value: energy[1], itemStyle: { color: '#909399' } }
        ],
        barMaxWidth: 50,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', formatter: '{c}' }
      }
    ]
  })
}

function onResize() {
  brightChart?.resize()
  compareChart?.resize()
}

onMounted(async () => {
  await loadLights()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  brightChart?.dispose()
  compareChart?.dispose()
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
  min-height: 320px;
}

.accuracy-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.accuracy-value {
  font-size: 44px;
  font-weight: 600;
  color: #409eff;
  text-align: center;
  margin-top: 30px;
}

.accuracy-source {
  text-align: center;
  margin-top: 10px;
}

.accuracy-detail {
  margin-top: 20px;
  padding: 0 12px;
}

.detail-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  padding-left: 2px;
}

.detail-item {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  transition: background 0.2s;
}

.detail-item.active {
  background: #ecf5ff;
  font-weight: 500;
  color: #409eff;
}

.detail-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
  flex-shrink: 0;
}

.detail-val {
  margin-left: auto;
  font-weight: 600;
  color: #303133;
}

.detail-item.active .detail-val {
  color: #409eff;
}

.accuracy-extra {
  text-align: center;
  color: #67c23a;
  margin-top: 16px;
  font-size: 13px;
}

/* 自动补全建议项样式 */
.suggest-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 0;
}

.suggest-code {
  font-weight: 600;
  color: #409eff;
  min-width: 90px;
}

.suggest-name {
  color: #303133;
  flex: 1;
}

.suggest-meta {
  font-size: 12px;
  color: #909399;
}
</style>
