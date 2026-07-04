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
        style="width: 160px"
        @change="onDistrictChange"
      >
        <el-option
          v-for="o in availableDistricts"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        >
          <span>{{ o.label }}</span>
          <span
            v-if="filter.road && o.hasRoad"
            style="margin-left: 6px; color: #67c23a; font-size: 12px"
          >✓ 包含{{ filter.road }}</span>
        </el-option>
      </el-select>
      <el-select
        v-model="filter.road"
        placeholder="路段"
        clearable
        style="width: 160px"
        @change="onRoadChange"
      >
        <el-option
          v-for="o in availableRoads"
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
        <el-table-column label="操作" width="180" fixed="right" class-name="table-ops">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              link
              type="success"
              :disabled="row.status === 2"
              @click="onSwitch(row, 1)"
            >开灯</el-button>
            <el-button
              v-else
              link
              type="info"
              :disabled="row.status === 2"
              @click="onSwitch(row, 0)"
            >关灯</el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status === 2"
              @click="openDim(row)"
            >调光</el-button>
            <el-tag
              v-if="row.status === 2"
              type="danger"
              size="small"
              style="margin-left: 4px"
            >故障</el-tag>
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
        <span class="map-layer-name">高德地图</span>
        <span class="map-info">
          <span class="map-info-item">缩放级别: <b>{{ currentZoom }}</b></span>
          <span class="map-info-item">比例尺: <b>{{ scaleText }}</b></span>
        </span>
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

    <!-- 路段跨行政区选择弹窗 -->
    <el-dialog
      v-model="districtSelectDialog"
      title="请选择行政区"
      width="360px"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <div v-if="pendingRoad" style="padding: 8px 0">
        <p style="margin: 0 0 16px 0; color: #606266; font-size: 14px">
          路段“<b>{{ pendingRoad }}</b>”存在于多个行政区：
        </p>
        <p style="margin: 0 0 16px 0; color: #f56c6c; font-size: 13px">
          {{ pendingDistricts.join('、') }}
        </p>
        <el-select v-model="selectedPendingDistrict" placeholder="请选择行政区" style="width: 100%">
          <el-option
            v-for="d in pendingDistricts"
            :key="d"
            :label="d"
            :value="d"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="onCancelDistrictSelect">取消</el-button>
        <el-button type="primary" :disabled="!selectedPendingDistrict" @click="onConfirmDistrictSelect">
          确定
        </el-button>
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

const roadDistrictsMap = computed(() => {
  const map = new Map()
  allLights.value.forEach((l) => {
    if (!l.road || !l.district) return
    if (!map.has(l.road)) {
      map.set(l.road, new Set())
    }
    map.get(l.road).add(l.district)
  })
  return map
})

const availableDistricts = computed(() => {
  const districts = new Set()
  allLights.value.forEach((l) => {
    if (l.district) districts.add(l.district)
  })
  const arr = Array.from(districts)
  if (arr.length === 0) {
    return DISTRICT_OPTIONS.map((o) => ({ ...o, hasRoad: false }))
  }
  return arr.map((d) => ({
    value: d,
    label: d,
    hasRoad: filter.road
      ? roadDistrictsMap.value.get(filter.road)?.has(d) ?? false
      : false
  }))
})

const availableRoads = computed(() => {
  const roads = new Set()
  allLights.value.forEach((l) => {
    if (l.road) roads.add(l.road)
  })
  const arr = Array.from(roads)
  if (arr.length === 0) {
    return ROAD_OPTIONS
  }
  return arr.map((r) => ({ value: r, label: r }))
})

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

  // 首次探测：用第一盏灯测试 API 是否可用
  if (sensorApiAvailable.value === null) {
    try {
      const res = await getLatestSensorData(lights[0].id)
      sensorApiAvailable.value = true
      applySensor(lights[0].id, res.data)
    } catch (e) {
      sensorApiAvailable.value = false
      return
    }
    // 首次调用：加载剩余路灯
    await runConcurrent(lights.slice(1), 8, async (l) => {
      try {
        const res = await getLatestSensorData(l.id)
        applySensor(l.id, res.data)
      } catch (e) {}
    })
  } else {
    // 后续调用：加载全部路灯（修复之前跳过 lights[0] 的 bug）
    await runConcurrent(lights, 8, async (l) => {
      try {
        const res = await getLatestSensorData(l.id)
        applySensor(l.id, res.data)
      } catch (e) {}
    })
  }
}

// 地图相关
const mapContainer = ref(null)
let mapInstance = null
let tileLayer = null
let markersLayer = null
let scaleControl = null
const markerMap = reactive({})

const mapLayer = ref('gaode')
const mapZoom = ref(13)
const mapCenter = ref([31.2304, 121.4737])
// 地图当前缩放级别（实时变化，用于显示）
const currentZoom = ref(13)
// 地图比例尺文字（实时变化）
const scaleText = ref('0 m')

const gaodeUrl = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const gaodeSubdomains = ['1', '2', '3', '4']

// 行政区中心坐标（用于根据区域自动跳转）
const DISTRICT_CENTERS = {
  '城东区': { center: [31.25, 121.55], zoom: 14 },
  '城西区': { center: [31.25, 121.40], zoom: 14 },
  '城南区': { center: [31.18, 121.48], zoom: 14 },
  '城北区': { center: [31.32, 121.48], zoom: 14 },
  '中心区': { center: [31.235, 121.4737], zoom: 15 }
}

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
  // 大圈套小圈：外层银色大环 + 状态色发光光晕 + 内层状态色小球
  const iconHtml = `<div class="light-marker ${getMarkerClass(light.status)}">
    ${light.status === 1 ? `<div class="marker-glow" style="background-color: ${color}"></div>` : ''}
    <div class="marker-outer">
      <div class="marker-inner" style="background-color: ${color}"></div>
    </div>
    ${light.status === 2 ? '<div class="marker-pulse"></div>' : ''}
  </div>`

  return L.divIcon({
    html: iconHtml,
    className: '',
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -20]
  })
}

function initMap() {
  if (!mapContainer.value) return

  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
  }

  // 清空 marker 映射表，因为旧 marker 已随旧地图销毁
  Object.keys(markerMap).forEach((key) => delete markerMap[key])

  mapInstance = L.map(mapContainer.value, {
    center: mapCenter.value,
    zoom: mapZoom.value,
    zoomControl: true,
    attributionControl: false
  })

  markersLayer = L.featureGroup().addTo(mapInstance)
  addTileLayer()

  // 添加比例尺控件
  if (scaleControl) {
    mapInstance.removeControl(scaleControl)
  }
  scaleControl = L.control.scale({
    position: 'bottomleft',
    imperial: false,
    metric: true,
    maxWidth: 120
  }).addTo(mapInstance)

  // 监听缩放事件，实时更新比例尺和缩放级别显示
  mapInstance.on('zoomend', () => {
    if (!mapInstance) return
    const z = mapInstance.getZoom()
    currentZoom.value = z
    scaleText.value = computeScaleText(z)
  })
  // 初始化比例尺文字
  currentZoom.value = mapInstance.getZoom()
  scaleText.value = computeScaleText(mapInstance.getZoom())
}

// 根据缩放级别估算比例尺文字（米）
function computeScaleText(zoom) {
  // 简化估算：每像素对应的米数（Web 墨卡托近似）
  const metersPerPixel = 156543.03392 * Math.cos((31.23 * Math.PI) / 180) / Math.pow(2, zoom)
  const meters = Math.round(metersPerPixel * 100) // 取 100 像素宽度
  if (meters >= 1000) {
    return (meters / 1000).toFixed(meters >= 5000 ? 0 : 1) + ' km'
  }
  return meters + ' m'
}

function addTileLayer() {
  if (!mapInstance) return

  if (tileLayer) {
    mapInstance.removeLayer(tileLayer)
    tileLayer = null
  }

  tileLayer = L.tileLayer(gaodeUrl, {
    subdomains: gaodeSubdomains,
    attribution: '© 高德地图',
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
  if (!mapInstance || !markersLayer) return

  const layers = markersLayer.getLayers()
  if (layers.length === 0) return

  try {
    const bounds = markersLayer.getBounds()
    if (bounds && bounds.isValid()) {
      if (layers.length === 1 && layers[0].getLatLng) {
        const ll = layers[0].getLatLng()
        mapInstance.setView([ll.lat, ll.lng], 15, { animate: true })
      } else {
        mapInstance.fitBounds(bounds, { padding: [60, 60], maxZoom: 16 })
      }
    }
  } catch (e) {
    if (layers[0] && layers[0].getLatLng) {
      mapInstance.setView(layers[0].getLatLng(), 15)
    }
  }
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
    if (mode.value === 'list') {
      await loadSensorData(pagedLights.value)
    } else {
      await loadSensorData(lightsWithLocation.value)
      await nextTick()
      renderMarkers()
    }
  } catch (e) {
    allLights.value = []
  } finally {
    loading.value = false
  }
}

function onManualRefresh() {
  refreshAll(true)
}

function applyFilter() {
  listQuery.pageNum = 1
  if (mode.value === 'list') {
    loadSensorData(pagedLights.value)
  } else {
    renderMarkers()
    loadSensorData(lightsWithLocation.value)
  }
}

function onFilterChange(changedField) {
  // 如果行政区改变，且当前路段不在该行政区内，清空路段
  if (changedField === 'district' && filter.road && filter.district) {
    const districts = roadDistrictsMap.value.get(filter.road)
    if (districts && !districts.has(filter.district)) {
      filter.road = undefined
    }
  }
  applyFilter()
}

function onDistrictChange(v) {
  onFilterChange('district', v)
}

const districtSelectDialog = ref(false)
const pendingRoad = ref('')
const pendingDistricts = ref([])
const selectedPendingDistrict = ref('')

function openDistrictSelect(road, districts) {
  pendingRoad.value = road
  pendingDistricts.value = Array.from(districts)
  selectedPendingDistrict.value = ''
  districtSelectDialog.value = true
}

function onCancelDistrictSelect() {
  districtSelectDialog.value = false
  filter.road = undefined
  applyFilter()
}

function onConfirmDistrictSelect() {
  if (!selectedPendingDistrict.value) return
  filter.district = selectedPendingDistrict.value
  districtSelectDialog.value = false
  applyFilter()
}

function onRoadChange(road) {
  if (!road) {
    filter.district = undefined
    applyFilter()
    return
  }

  const districts = roadDistrictsMap.value.get(road)

  // 如果当前行政区已选择且包含该路段，则保持
  if (filter.district && districts?.has(filter.district)) {
    applyFilter()
    return
  }

  // 只有一个行政区包含该路段，自动填入
  if (districts && districts.size === 1) {
    filter.district = Array.from(districts)[0]
    applyFilter()
    return
  }

  // 多个行政区包含该路段，要求用户选择
  if (districts && districts.size > 1) {
    filter.district = undefined
    applyFilter()
    openDistrictSelect(road, districts)
    return
  }

  applyFilter()
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
    // 同步更新地图上的 marker 图标（无论当前模式，确保切换到地图时状态正确）
    if (markerMap[row.id]) {
      markerMap[row.id].setIcon(createMarkerIcon(row))
    }
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
  scheduleNext()
}
function scheduleNext() {
  // 用递归 setTimeout 替代 setInterval，确保上一次 refreshAll 完成后再调度下一次，
  // 避免请求慢于间隔时多次重叠导致旧响应覆盖新数据
  timer = setTimeout(async () => {
    if (!autoRefresh.value) return
    try {
      await refreshAll(false)
    } catch (e) {}
    if (autoRefresh.value) scheduleNext()
  }, interval.value * 1000)
}
function stopPolling() {
  if (timer) {
    clearTimeout(timer)
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
    // 确保地图容器尺寸正确后再居中
    setTimeout(() => {
      if (mapInstance) {
        mapInstance.invalidateSize()
        updateMapBounds()
      }
    }, 100)
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

.map-info {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-left: 8px;
  padding: 4px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
}

.map-info-item b {
  color: #303133;
  font-weight: 600;
  margin-left: 2px;
}

.map-layer-name {
  display: inline-block;
  padding: 4px 12px;
  background-color: #409eff;
  color: #fff;
  font-size: 12px;
  border-radius: 4px;
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

  .map-info {
    margin-left: 0;
    gap: 8px;
    font-size: 11px;
  }

  .detail-body {
    grid-template-columns: 80px 1fr;
  }
}
</style>

<style>
/* Leaflet marker 全局样式 - Leaflet 动态插入的 DOM 不在 Vue scoped 作用域内 */
.light-marker {
  position: relative;
  width: 40px;
  height: 40px;
  cursor: pointer;
}

.marker-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 40px;
  height: 40px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background-color: #67c23a;
  opacity: 0.25;
  filter: blur(4px);
  animation: marker-glow 2s ease-in-out infinite;
  pointer-events: none;
}

@keyframes marker-glow {
  0%, 100% {
    opacity: 0.25;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 0.45;
    transform: translate(-50%, -50%) scale(1.2);
  }
}

.marker-outer {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 28px;
  height: 28px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: linear-gradient(135deg, #e8eaec 0%, #c0c4cc 50%, #909399 100%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.marker-inner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  box-shadow: 0 0 3px rgba(255, 255, 255, 0.6) inset;
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
  top: 50%;
  left: 50%;
  width: 28px;
  height: 28px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background-color: rgba(245, 108, 108, 0.4);
  animation: marker-pulse 1.5s ease-out infinite;
  pointer-events: none;
  z-index: 1;
}

@keyframes marker-pulse {
  0% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -50%) scale(2.2);
    opacity: 0;
  }
}

/* 修复 Leaflet divIcon 默认的白色背景 */
.leaflet-div-icon {
  background: transparent;
  border: none;
}
</style>
