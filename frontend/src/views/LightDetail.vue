<template>
  <div class="page-container">
    <div class="page-header">
      <div style="display:flex;align-items:center;gap:12px">
        <el-button :icon="ArrowLeft" @click="$router.push('/devices/archive')">返回</el-button>
        <h2 class="page-title">路灯详情</h2>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <!-- 设备信息 -->
    <div class="detail-card">
      <div class="detail-header">
        <div>
          <span class="detail-name">{{ light.lightName || '-' }}</span>
          <el-tag :type="LIGHT_STATUS_MAP[light.status]?.type" size="small" style="margin-left:8px">
            {{ LIGHT_STATUS_MAP[light.status]?.label }}
          </el-tag>
        </div>
        <span class="detail-code">{{ light.lightCode }}</span>
      </div>
      <el-descriptions :column="3" border size="default">
        <el-descriptions-item label="安装位置">{{ light.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经度">{{ light.longitude ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="纬度">{{ light.latitude ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备类型">{{ light.deviceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="额定功率(W)">{{ light.ratedPower ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前亮度(%)">
          <el-slider
            v-model="light.brightness"
            :min="0"
            :max="100"
            :disabled="light.status === 2"
            style="width:160px;display:inline-flex;vertical-align:middle"
            @change="onBrightnessChange"
          />
          <span style="margin-left:8px">{{ light.brightness }}%</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(light.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(light.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ light.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 最新传感器数据 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="10">
        <div class="detail-card">
          <div class="card-title">最新传感器数据</div>
          <div v-if="latest" class="latest-grid">
            <div class="metric" style="--c:#f5a623">
              <div class="m-label">光照强度</div>
              <div class="m-value">{{ latest.illuminance ?? '-' }}<span>lux</span></div>
            </div>
            <div class="metric" style="--c:#409eff">
              <div class="m-label">当前功率</div>
              <div class="m-value">{{ latest.power ?? '-' }}<span>W</span></div>
            </div>
            <div class="metric" style="--c:#67c23a">
              <div class="m-label">电压</div>
              <div class="m-value">{{ latest.voltage ?? '-' }}<span>V</span></div>
            </div>
            <div class="metric" style="--c:#e6a23c">
              <div class="m-label">电流</div>
              <div class="m-value">{{ latest.current ?? '-' }}<span>A</span></div>
            </div>
            <div class="metric" style="--c:#f56c6c">
              <div class="m-label">温度</div>
              <div class="m-value">{{ latest.temperature ?? '-' }}<span>°C</span></div>
            </div>
            <div class="metric" style="--c:#909399">
              <div class="m-label">湿度</div>
              <div class="m-value">{{ latest.humidity ?? '-' }}<span>%RH</span></div>
            </div>
            <div class="metric" style="--c:#9c27b0">
              <div class="m-label">累计耗电</div>
              <div class="m-value">{{ latest.totalEnergy != null ? latest.totalEnergy.toFixed(2) : '-' }}<span>kWh</span></div>
            </div>
            <div class="metric full" style="--c:#0c2461">
              <div class="m-label">采集时间</div>
              <div class="m-value" style="font-size:18px">{{ formatDateTime(latest.collectTime) }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无传感器数据" :image-size="80" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="14">
        <div class="detail-card">
          <div class="card-title" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
            <span>近 1 小时平均值</span>
            <el-button link type="primary" :icon="Refresh" @click="loadAverage">刷新</el-button>
          </div>
          <div v-if="average">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="平均光照(lux)">{{ average.illuminance?.toFixed(2) ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="平均功率(W)">{{ average.power?.toFixed(2) ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="平均电压(V)">{{ average.voltage?.toFixed(2) ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="平均电流(A)">{{ average.current?.toFixed(3) ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="平均温度(°C)">{{ average.temperature?.toFixed(2) ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="平均湿度(%RH)">{{ average.humidity?.toFixed(2) ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="平均累计耗电(kWh)">{{ average.totalEnergy != null ? average.totalEnergy.toFixed(2) : '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else description="暂无统计数据" :image-size="80" />
        </div>
      </el-col>
    </el-row>

    <!-- 历史传感器数据 -->
    <div class="detail-card">
      <div class="card-title">历史传感器数据</div>
      <div class="table-card" style="box-shadow:none">
        <el-table :data="history" stripe size="default">
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="illuminance" label="光照(lux)" />
          <el-table-column prop="power" label="功率(W)" />
          <el-table-column prop="voltage" label="电压(V)" />
          <el-table-column prop="current" label="电流(A)" />
          <el-table-column prop="temperature" label="温度(°C)" />
          <el-table-column prop="humidity" label="湿度(%RH)" />
          <el-table-column label="累计耗电(kWh)" width="130">
            <template #default="{ row }">
              {{ row.totalEnergy != null ? row.totalEnergy.toFixed(2) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="采集时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.collectTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="hQuery.pageNum"
            v-model:page-size="hQuery.pageSize"
            :total="hTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadHistory"
            @current-change="loadHistory"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { getLightById, setLightBrightness } from '@/api/light'
import {
  getLatestSensorData,
  getAverageSensorData,
  getSensorDataPage
} from '@/api/sensor'
import { LIGHT_STATUS_MAP } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const lightId = Number(route.params.id)

const loading = ref(false)
const light = ref({})
const latest = ref(null)
const average = ref(null)
const history = ref([])
const hTotal = ref(0)

const hQuery = reactive({ pageNum: 1, pageSize: 10 })

async function loadLight() {
  const res = await getLightById(lightId)
  light.value = res.data || {}
}

async function loadLatest() {
  try {
    const res = await getLatestSensorData(lightId)
    latest.value = res.data || null
  } catch (e) {
    latest.value = null
  }
}

async function loadAverage() {
  try {
    const res = await getAverageSensorData(lightId)
    average.value = res.data || null
  } catch (e) {
    average.value = null
  }
}

async function loadHistory() {
  try {
    const res = await getSensorDataPage({ ...hQuery, lightId })
    history.value = res.data?.records || []
    hTotal.value = res.data?.total || 0
  } catch (e) {
    // ignore
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadLight(), loadLatest(), loadAverage(), loadHistory()])
  } finally {
    loading.value = false
  }
}

async function onBrightnessChange(val) {
  const oldVal = light.value.brightness
  try {
    await setLightBrightness(lightId, val)
    ElMessage.success('亮度已更新')
  } catch (e) {
    light.value.brightness = oldVal
    ElMessage.error('亮度更新失败')
  }
}

onMounted(loadAll)
</script>

<style scoped>
.detail-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--card-shadow);
  margin-bottom: 16px;
}

.detail-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-name {
  font-size: 18px;
  font-weight: 600;
}

.detail-code {
  color: #909399;
  font-size: 13px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.latest-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.metric {
  background: #f7f9fc;
  border-radius: 8px;
  padding: 14px;
  border-top: 3px solid var(--c);
}

.metric.full {
  grid-column: 1 / -1;
}

.m-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.m-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.m-value span {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
  font-weight: 400;
}

@media (max-width: 640px) {
  .latest-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
