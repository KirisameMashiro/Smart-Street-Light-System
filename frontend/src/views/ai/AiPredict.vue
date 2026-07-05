<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">AI 预测调光</h2>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <!-- 顶部控制 -->
    <div class="filter-bar">
      <el-select
        v-model="lightId"
        placeholder="选择路灯"
        filterable
        clearable
        style="width: 260px"
        @change="onLightChange"
      >
        <el-option
          v-for="l in lightOptions"
          :key="l.id"
          :label="`${l.lightCode} - ${l.lightName || ''}`"
          :value="l.id"
        />
      </el-select>
      <el-tag :type="predictActive ? 'success' : 'info'" size="default">
        预测模式：{{ predictActive ? '已启用' : '未启用' }}
      </el-tag>
      <el-button
        type="primary"
        :icon="VideoPlay"
        :disabled="!lightId || predictActive"
        :loading="applying"
        @click="onApply"
      >启用预测模式</el-button>
      <el-button
        type="danger"
        :icon="VideoPause"
        :disabled="!lightId || !predictActive"
        :loading="stopping"
        @click="onStop"
      >停用预测模式</el-button>
    </div>

    <!-- 算法说明 -->
    <div class="chart-card">
      <div class="chart-title">算法说明</div>
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          基于历史同期光照数据、实时天气 API 与时段特征，采用 ARIMA / 多项式拟合并叠加天气修正，预测未来 24 小时逐时推荐亮度，并校验不低于最低安全照度，保障通行安全。
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
      <div v-else ref="brightRef" class="chart-box" v-loading="loading"></div>
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
          <div v-else ref="compareRef" class="chart-box" v-loading="loading"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="chart-card accuracy-card">
          <div class="chart-title">预测准确率</div>
          <div class="accuracy-value">
            {{ compareError || !lightId ? '—' : accuracy + '%' }}
          </div>
          <div class="accuracy-desc">基于历史回测数据</div>
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
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getPredictResult,
  applyPredictMode,
  stopPredictMode,
  getPredictCompare
} from '@/api/predict'
import { getAllLights } from '@/api/light'
import { logOperation } from '@/utils/log'

const loading = ref(false)
const applying = ref(false)
const stopping = ref(false)
const lightId = ref(undefined)
const lightOptions = ref([])
const predictActive = ref(false)

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

function onLightChange() {
  predictActive.value = false
  loadAll()
}

async function loadAll() {
  if (!lightId.value) return
  loading.value = true
  try {
    await Promise.all([loadResult(), loadCompare()])
    await nextTick()
    renderBright()
    renderCompare()
  } finally {
    loading.value = false
  }
}

function renderBright() {
  if (!brightRef.value || resultError.value) return
  brightChart = brightChart || echarts.init(brightRef.value)
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
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: hours, name: '小时' },
    yAxis: { type: 'value', name: '亮度(%)', min: 0, max: 100 },
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
  compareChart = compareChart || echarts.init(compareRef.value)
  const d = compareData.value || {}
  const categories = ['预测模式', '固定阈值模式']
  const energy = [Number(d.predictEnergy ?? 0), Number(d.fixedEnergy ?? 0)]
  compareChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 55, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: categories },
    yAxis: { type: 'value', name: '能耗(kWh)' },
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

function lightLabel(id) {
  const l = lightOptions.value.find((x) => x.id === id)
  return l ? `${l.lightCode}-${l.lightName || ''}` : id
}

async function onApply() {
  if (!lightId.value) {
    ElMessage.warning('请先选择路灯')
    return
  }
  applying.value = true
  try {
    await applyPredictMode(lightId.value)
    predictActive.value = true
    ElMessage.success('预测模式已启用')
    await logOperation(
      'predict_apply',
      `启用路灯 ${lightLabel(lightId.value)} 预测调光模式`,
      '成功'
    )
    loadAll()
  } catch (e) {
    await logOperation(
      'predict_apply',
      `启用路灯 ${lightLabel(lightId.value)} 预测调光模式`,
      '失败'
    )
  } finally {
    applying.value = false
  }
}

async function onStop() {
  if (!lightId.value) return
  stopping.value = true
  try {
    await stopPredictMode(lightId.value)
    predictActive.value = false
    ElMessage.success('预测模式已停用')
    await logOperation(
      'predict_apply',
      `停用路灯 ${lightLabel(lightId.value)} 预测调光模式`,
      '成功'
    )
  } catch (e) {
    await logOperation(
      'predict_apply',
      `停用路灯 ${lightLabel(lightId.value)} 预测调光模式`,
      '失败'
    )
  } finally {
    stopping.value = false
  }
}

function onResize() {
  brightChart?.resize()
  compareChart?.resize()
}

onMounted(async () => {
  await loadLights()
  if (lightOptions.value.length && !lightId.value) {
    lightId.value = lightOptions.value[0].id
    loadAll()
  }
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
  height: 300px;
  width: 100%;
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

.accuracy-desc {
  text-align: center;
  color: #909399;
  margin-top: 8px;
  font-size: 13px;
}

.accuracy-extra {
  text-align: center;
  color: #67c23a;
  margin-top: 16px;
  font-size: 13px;
}
</style>
