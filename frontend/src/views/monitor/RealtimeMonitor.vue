<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">实时监测</h2>
      <div class="toolbar">
        <el-radio-group v-model="mode">
          <el-radio-button value="list">列表模式</el-radio-button>
          <el-radio-button value="map">地图模式</el-radio-button>
        </el-radio-group>
        <el-divider direction="vertical" />
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
        <el-button :icon="Refresh" :loading="loading" @click="onManualRefresh">刷新</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select
        v-model="filter.district"
        placeholder="行政区"
        clearable
        style="width: 140px"
        @change="onFilterChange"
      >
        <el-option
          v-for="o in DISTRICT_OPTIONS"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <el-select
        v-model="filter.road"
        placeholder="路段"
        clearable
        style="width: 140px"
        @change="onFilterChange"
      >
        <el-option
          v-for="o in ROAD_OPTIONS"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <span class="text-muted" style="margin-left: auto">
        共 {{ filteredLights.length }} 盏
        <template v-if="mode === 'list'">
          ｜在线 {{ onlineCount }} ｜故障 {{ faultCount }}
        </template>
      </span>
    </div>

    <!-- 列表模式 -->
    <div v-if="mode === 'list'" class="table-card">
      <el-table :data="pagedLights" v-loading="loading" stripe>
        <el-table-column prop="lightCode" label="编号" width="110" />
        <el-table-column prop="lightName" label="名称" width="130" show-overflow-tooltip />
        <el-table-column prop="location" label="位置" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag
              :type="LIGHT_STATUS_MAP[row.status]?.type"
              size="small"
              :effect="row.status === 2 ? 'dark' : 'light'"
            >
              {{ LIGHT_STATUS_MAP[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最新光照(lux)" width="120">
          <template #default="{ row }">
            {{ sensorMap[row.id]?.illuminance ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="最新功率(W)" width="120">
          <template #default="{ row }">
            {{ sensorMap[row.id]?.power ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="累计耗电(kWh)" width="120">
          <template #default="{ row }">
            {{ sensorMap[row.id]?.totalEnergy != null ? sensorMap[row.id].totalEnergy.toFixed(2) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" class-name="table-ops">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              link
              type="success"
              @click="onSwitch(row, 1)"
            >开灯</el-button>
            <el-button
              v-else
              link
              type="info"
              @click="onSwitch(row, 0)"
            >关灯</el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status === 2"
              @click="openDim(row)"
            >调光</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="listQuery.pageNum"
          v-model:page-size="listQuery.pageSize"
          :total="filteredLights.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>

    <!-- 地图模式 -->
    <div v-else class="map-card">
      <div class="map-header">
        <span class="text-muted">底图：</span>
        <el-radio-group v-model="mapLayer" size="small" @change="onLayerChange">
          <el-radio-button value="gaode">高德地图</el-radio-button>
          <el-radio-button value="osm">OpenStreetMap</el-radio-button>
        </el-radio-group>
        <span class="text-muted" style="margin-left: auto">
          在线 <span class="status-dot online"></span> {{ onlineCount }}
          故障 <span class="status-dot fault"></span> {{ faultCount }}
          离线 <span class="status-dot offline"></span> {{ offlineCount }}
        </span>
      </div>
      <div ref="mapContainer" class="map-wrapper"></div>
    </div>

    <!-- 设备详情弹窗 -->
    <el-dialog v-model="detailDialog" title="设备详情" width="420px" :close-on-click-modal="false">
      <div v-if="selectedLight" class="light-detail">
        <div class="detail-header">
          <div class="detail-icon" :class="getMarkerClass(selectedLight.status)">
            {{ getStatusIcon(selectedLight.status) }}
          </div>
          <div class="detail-info">
            <h3>{{ selectedLight.lightName || selectedLight.lightCode }}</h3>
            <div class="detail-status">
              <el-tag
                :type="LIGHT_STATUS_MAP[selectedLight.status]?.type"
                size="small"
              >
                {{ LIGHT_STATUS_MAP[selectedLight.status]?.label }}
              </el-tag>
            </div>
          </div>
        </div>
        <div class="detail-body">
          <div class="detail-row">
            <span class="detail-label">设备编号</span>
            <span class="detail-value">{{ selectedLight.lightCode }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">安装位置</span>
            <span class="detail-value">{{ selectedLight.location || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">行政区</span>
            <span class="detail-value">{{ selectedLight.district || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">路段</span>
            <span class="detail-value">{{ selectedLight.road || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">当前亮度</span>
            <span class="detail-value">{{ selectedLight.brightness || 0 }}%</span>
          </div>
          <div class="detail-row" v-if="sensorMap[selectedLight.id]">
            <span class="detail-label">光照强度</span>
            <span class="detail-value">{{ sensorMap[selectedLight.id].illuminance }} lux</span>
          </div>
          <div class="detail-row" v-if="sensorMap[selectedLight.id]">
            <span class="detail-label">功率消耗</span>
            <span class="detail-value">{{ sensorMap[selectedLight.id].power }} W</span>
          </div>
          <div class="detail-row" v-if="sensorMap[selectedLight.id] && sensorMap[selectedLight.id].totalEnergy != null">
            <span class="detail-label">累计耗电</span>
            <span class="detail-value">{{ sensorMap[selectedLight.id].totalEnergy.toFixed(2) }} kWh</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
        <el-button
          v-if="selectedLight && selectedLight.status !== 2"
          type="primary"
          @click="openDim(selectedLight); detailDialog = false"
        >调光</el-button>
      </template>
    </el-dialog>

    <!-- 调光弹窗 -->
    <el-dialog v-model="dimDialog" title="调光控制" width="380px">
      <div v-if="dimRow" style="text-align: center">
        <div style="margin-bottom: 16px; color: #606266">
          <b>{{ dimRow.lightCode }}</b>
          <span v-if="dimRow.lightName"> - {{ dimRow.lightName }}</span>
        </div>
        <el-slider
          v-model="dimValue"
          :min="0"
          :max="100"
          :disabled="dimRow.status === 2"
          style="padding: 0 16px"
        />
        <div style="margin-top: 8px; font-size: 13px; color: #909399">
          当前亮度：{{ dimValue }}%
        </div>
      </div>
      <template #footer>
        <el-button @click="dimDialog = false">取消</el-button>
        <el-button type="primary" :loading="dimLoading" @click="onDim">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
  getAllLights,
  batchSwitchLight,
  setLightBrightness
} from '@/api/light'
import { getLatestSensorData } from '@/api/sensor'
import {
  LIGHT_STATUS_MAP,
  DISTRICT_OPTIONS,
  ROAD_OPTIONS,
  STATUS_COLORS
} from '@/utils/constants'
import { logOperation } from '@/utils/log'

const loading = ref(false)
const mode = ref('list')
const autoRefresh = ref(false)
const interval = ref(5)

const allLights = ref([])
const sensorMap = reactive({})
const sensorApiAvailable = ref(null)

const filter = reactive({ district: undefined, road: undefined })
const listQuery = reactive({ pageNum: 1, pageSize: 10 })

const filteredLights = computed(() =>
  allLights.value.filter((l) => {
    if (filter.district && l.district !== filter.district) return false
    if (filter.road && l.road !== filter.road) return false
    return true
  })
)

const pagedLights = computed(() => {
  const start = (listQuery.pageNum - 1) * listQuery.pageSize
  return filteredLights.value.slice(start, start + listQuery.pageSize)
})

const onlineCount = computed(
  () => filteredLights.value.filter((l) => l.status === 1).length
)
const faultCount = computed(
  () => filteredLights.value.filter((l) => l.status === 2).length
)
const offlineCount = computed(
  () => filteredLights.value.filter((l) => l.status === 0).length
)

const lightsWithLocation = computed(() =>
  filteredLights.value.filter(
    (l) => l.longitude != null && l.latitude != null && l.longitude !== 0 && l.latitude !== 0
  )
)

function applySensor(id, d) {
  if (!d) {
    delete sensorMap[id]
    return
  }
  sensorMap[id] = {
    illuminance: d.illuminance,
    power: d.power,
    totalEnergy: d.totalEnergy,
    collectTime: d.collectTime
  }
}

async function runConcurrent(items, limit, fn) {
  const queue = [...items]
  async function worker() {
    while (queue.length) {
      const item = queue.shift()
      if (!item) break
      try {
        await fn(item)
      } catch (e) {}
    }
  }
  const workers = Array.from(
    { length: Math.min(limit, items.length) },
    worker
  )
  await Promise.all(workers)
}

async function loadSensorData(lights) {
  if (!lights || lights.length === 0) return
  if (sensorApiAvailable.value === false) return

  if (sensorApiAvailable.value === null) {
    try {
      const res = await getLatestSensorData(lights[0].id)
      sensorApiAvailable.value = true
      applySensor(lights[0].id, res.data)
    } catch (e) {
      sensorApiAvailable.value = false
      return
    }
  }

  await runConcurrent(lights.slice(1), 8, async (l) => {
    try {
      const res = await getLatestSensorData(l.id)
      applySensor(l.id, res.data)
    } catch (e) {}
  })
}

// 地图相关
const mapContainer = ref(null)
let mapInstance = null
let tileLayer = null
let markersLayer = null
const markerMap = reactive({})

const mapLayer = ref('gaode')
const mapZoom = ref(13)
const mapCenter = ref([31.2304, 121.4737])

const gaodeUrl = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const gaodeSubdomains = ['1', '2', '3', '4']
const osmUrl = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const osmSubdomains = ['a', 'b', 'c']

function getMarkerColor(status) {
  if (status === 1) return '#67c23a'
  if (status === 2) return '#f56c6c'
  return '#909399'
}

function getStatusIcon(status) {
  if (status === 1) return '◎'
  if (status === 2) return '✕'
  return '○'
}

function getMarkerClass(status) {
  if (status === 1) return 'marker-online'
  if (status === 2) return 'marker-fault'
  return 'marker-offline'
}

function createMarkerIcon(light) {
  const color = getMarkerColor(light.status)
  const iconHtml = `<div class="light-marker ${getMarkerClass(light.status)}">
    <div class="marker-inner" style="background-color: ${color}">
      <span class="marker-icon">${getStatusIcon(light.status)}</span>
    </div>
    ${light.status === 2 ? '<div class="marker-pulse"></div>' : ''}
  </div>`
  
  return L.divIcon({
    html: iconHtml,
    className: '',
    iconSize: [28, 28],
    iconAnchor: [14, 28],
    popupAnchor: [0, -28]
  })
}

function initMap() {
  if (!mapContainer.value) return
  
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
  }

  mapInstance = L.map(mapContainer.value, {
    center: mapCenter.value,
    zoom: mapZoom.value,
    zoomControl: true,
    attributionControl: false
  })

  markersLayer = L.layerGroup().addTo(mapInstance)
  addTileLayer()
}

function addTileLayer() {
  if (!mapInstance) return
  
  if (tileLayer) {
    mapInstance.removeLayer(tileLayer)
    tileLayer = null
  }

  const url = mapLayer.value === 'gaode' ? gaodeUrl : osmUrl
  const subdomains = mapLayer.value === 'gaode' ? gaodeSubdomains : osmSubdomains

  tileLayer = L.tileLayer(url, {
    subdomains,
    attribution: mapLayer.value === 'gaode' ? '© 高德地图' : '© OpenStreetMap contributors',
    maxZoom: 18,
    minZoom: 3
  }).addTo(mapInstance)
}

function renderMarkers() {
  if (!markersLayer) return

  const currentIds = new Set()

  lightsWithLocation.value.forEach((light) => {
    currentIds.add(light.id)
    
    if (markerMap[light.id]) {
      const marker = markerMap[light.id]
      const icon = createMarkerIcon(light)
      marker.setIcon(icon)
    } else {
      const icon = createMarkerIcon(light)
      const marker = L.marker(
        [Number(light.latitude), Number(light.longitude)],
        { icon }
      ).addTo(markersLayer)
      
      marker.on('click', () => {
        onMarkerClick(light)
      })
      
      markerMap[light.id] = marker
    }
  })

  Object.keys(markerMap).forEach((id) => {
    if (!currentIds.has(Number(id))) {
      markersLayer.removeLayer(markerMap[id])
      delete markerMap[id]
    }
  })

  updateMapBounds()
}

function updateMapBounds() {
  if (!markersLayer || markersLayer.getLayers().length === 0) return
  
  const bounds = markersLayer.getBounds()
  if (bounds.isValid()) {
    mapInstance.fitBounds(bounds, { padding: [50, 50] })
  }
}

function onLayerChange() {
  addTileLayer()
}

const detailDialog = ref(false)
const selectedLight = ref(null)

function onMarkerClick(light) {
  selectedLight.value = light
  loadSensorData([light])
  detailDialog.value = true
}

async function refreshAll(reprobe = false) {
  if (reprobe) sensorApiAvailable.value = null
  loading.value = true
  try {
    const res = await getAllLights()
    allLights.value = res.data || []
  } catch (e) {
    allLights.value = []
    loading.value = false
    return
  }
  if (mode.value === 'list') {
    await loadSensorData(pagedLights.value)
  } else {
    await loadSensorData(lightsWithLocation.value)
    await nextTick()
    renderMarkers()
  }
  loading.value = false
}

function onManualRefresh() {
  refreshAll(true)
}

function onFilterChange() {
  listQuery.pageNum = 1
  if (mode.value === 'list') {
    loadSensorData(pagedLights.value)
  } else {
    renderMarkers()
    loadSensorData(lightsWithLocation.value)
  }
}

async function onSwitch(row, status) {
  try {
    await batchSwitchLight([row.id], status)
    row.status = status
    if (status === 0) row.brightness = 0
    ElMessage.success(status === 1 ? '已开灯' : '已关灯')
    logOperation(
      status === 1 ? 'switch_on' : 'switch_off',
      `${status === 1 ? '开启' : '关闭'}路灯 ${row.lightCode || row.lightName}`,
      '成功'
    )
  } catch (e) {}
}

const dimDialog = ref(false)
const dimRow = ref(null)
const dimValue = ref(0)
const dimLoading = ref(false)

function openDim(row) {
  dimRow.value = row
  dimValue.value = row.brightness ?? 0
  dimDialog.value = true
}

async function onDim() {
  if (!dimRow.value) return
  dimLoading.value = true
  try {
    await setLightBrightness(dimRow.value.id, dimValue.value)
    dimRow.value.brightness = dimValue.value
    ElMessage.success(`亮度已调整为 ${dimValue.value}%`)
    logOperation(
      'dimming',
      `调光 ${dimRow.value.lightCode || dimRow.value.lightName} 至 ${dimValue.value}%`,
      '成功'
    )
    dimDialog.value = false
  } catch (e) {} finally {
    dimLoading.value = false
  }
}

let timer = null
function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    refreshAll(false)
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

watch(
  [() => listQuery.pageNum, () => listQuery.pageSize],
  () => {
    if (mode.value === 'list') {
      loadSensorData(pagedLights.value)
    }
  }
)

watch(mode, async (m) => {
  if (m === 'map') {
    await nextTick()
    initMap()
    renderMarkers()
    loadSensorData(lightsWithLocation.value)
  } else {
    await loadSensorData(pagedLights.value)
  }
})

function onResize() {
  if (mapInstance) {
    mapInstance.invalidateSize()
  }
}

onMounted(async () => {
  await refreshAll(false)
  startPolling()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('resize', onResize)
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
  }
})
</script>

<style scoped>
.map-card {
  height: 560px;
  display: flex;
  flex-direction: column;
}

.map-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px 8px 0 0;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.map-wrapper {
  flex: 1;
  border-radius: 0 0 8px 8px;
  overflow: hidden;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.status-dot.online {
  background-color: #67c23a;
}

.status-dot.fault {
  background-color: #f56c6c;
}

.status-dot.offline {
  background-color: #909399;
}

.light-marker {
  position: relative;
  width: 28px;
  height: 28px;
  cursor: pointer;
}

.marker-inner {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  font-weight: bold;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  border: 2px solid #fff;
}

.marker-online .marker-inner {
  background-color: #67c23a;
}

.marker-fault .marker-inner {
  background-color: #f56c6c;
}

.marker-offline .marker-inner {
  background-color: #909399;
}

.marker-pulse {
  position: absolute;
  top: -3px;
  left: -3px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background-color: rgba(245, 108, 108, 0.3);
  animation: pulse 1.5s ease-out infinite;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(2);
    opacity: 0;
  }
}

.light-detail {
  padding: 8px 0;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
}

.detail-icon.marker-online {
  background-color: #67c23a;
}

.detail-icon.marker-fault {
  background-color: #f56c6c;
}

.detail-icon.marker-offline {
  background-color: #909399;
}

.detail-info h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.detail-status {
  margin-top: 4px;
}

.detail-body {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 12px 8px;
}

.detail-row {
  display: contents;
}

.detail-label {
  color: #909399;
  font-size: 13px;
}

.detail-value {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

@media (max-width: 768px) {
  .map-card {
    height: 400px;
  }

  .map-header {
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px 12px;
  }

  .map-header .text-muted:last-child {
    margin-left: 0;
    margin-top: 4px;
  }

  .detail-body {
    grid-template-columns: 80px 1fr;
  }
}
</style>
