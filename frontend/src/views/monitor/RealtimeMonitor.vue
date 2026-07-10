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
      <el-select
        v-model="filter.status"
        placeholder="状态"
        clearable
        style="width: 120px"
        @change="applyFilter"
      >
        <el-option label="开启" :value="1" />
        <el-option label="关闭" :value="0" />
        <el-option label="故障" :value="2" />
      </el-select>
      <span class="text-muted" style="margin-left: auto">
        共 {{ page.total }} 盏
        <template v-if="mode === 'list'">
          ｜在线 {{ stats.online }} ｜故障 {{ stats.fault }} ｜离线 {{ stats.offline }}
        </template>
      </span>
    </div>

    <!-- 列表模式 -->
    <div v-if="mode === 'list'" class="table-card">
      <div class="table-wrapper">
        <el-table :data="page.records" stripe>
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
          <el-table-column label="电压(V)" width="90">
            <template #default="{ row }">
              {{ sensorMap[row.id]?.voltage != null ? sensorMap[row.id].voltage.toFixed(1) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="电流(A)" width="90">
            <template #default="{ row }">
              {{ sensorMap[row.id]?.current != null ? sensorMap[row.id].current.toFixed(3) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="温度(°C)" width="95">
            <template #default="{ row }">
              {{ sensorMap[row.id]?.temperature != null ? sensorMap[row.id].temperature.toFixed(1) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="湿度(%RH)" width="100">
            <template #default="{ row }">
              {{ sensorMap[row.id]?.humidity != null ? sensorMap[row.id].humidity.toFixed(1) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="今日累计耗电(Wh)" width="140">
          <template #default="{ row }">
            {{ cumulativeEnergyMap[row.id] != null ? cumulativeEnergyMap[row.id].toFixed(3) : '-' }}
          </template>
        </el-table-column>
        </el-table>
      </div>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="listQuery.pageNum"
          v-model:page-size="listQuery.pageSize"
          :total="page.total"
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
          在线 <span class="status-dot online"></span> {{ stats.online }}
          故障 <span class="status-dot fault"></span> {{ stats.fault }}
          离线 <span class="status-dot offline"></span> {{ stats.offline }}
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
            <span class="detail-label">设备类型</span>
            <span class="detail-value">{{ selectedLight.deviceType || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">额定功率</span>
            <span class="detail-value">{{ selectedLight.ratedPower || '-' }} W</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">当前亮度</span>
            <span class="detail-value">{{ selectedLight.brightness || 0 }}%</span>
          </div>
          <div v-if="sensorMap[selectedLight.id]" class="sensor-section">
            <h4 class="sensor-title">传感器数据</h4>
            <div class="detail-row">
              <span class="detail-label">光照强度</span>
              <span class="detail-value">{{ sensorMap[selectedLight.id].illuminance }} lux</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">功率消耗</span>
              <span class="detail-value">{{ sensorMap[selectedLight.id].power }} W</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">电压</span>
              <span class="detail-value">{{ sensorMap[selectedLight.id].voltage != null ? sensorMap[selectedLight.id].voltage.toFixed(1) : '-' }} V</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">电流</span>
              <span class="detail-value">{{ sensorMap[selectedLight.id].current != null ? sensorMap[selectedLight.id].current.toFixed(3) : '-' }} A</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">温度</span>
              <span class="detail-value">{{ sensorMap[selectedLight.id].temperature != null ? sensorMap[selectedLight.id].temperature.toFixed(1) : '-' }} °C</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">湿度</span>
              <span class="detail-value">{{ sensorMap[selectedLight.id].humidity != null ? sensorMap[selectedLight.id].humidity.toFixed(1) : '-' }} %RH</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">累计耗电</span>
              <span class="detail-value">{{ cumulativeEnergyMap[selectedLight.id] != null ? cumulativeEnergyMap[selectedLight.id].toFixed(3) : '-' }} Wh</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">采集时间</span>
              <span class="detail-value">{{ sensorMap[selectedLight.id].collectTime || '-' }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
        <el-button type="primary" @click="goToDetail">查看详情</el-button>
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
          路段"<b>{{ pendingRoad }}</b>"存在于多个行政区：
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
defineOptions({ name: 'RealtimeMonitor' })
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getLightPage, getAllLights, getLightById } from '@/api/light'
import { getAllLatestSensorData, getLatestSensorData, getTodayEnergy } from '@/api/sensor'
import { LIGHT_STATUS_MAP } from '@/utils/constants'
import { useAppStore } from '@/store/app'

const appStore = useAppStore()

const loading = ref(false)
const mode = ref('list')
const autoRefresh = ref(false)
const interval = ref(1)
const router = useRouter()

const page = ref({ records: [], total: 0 })
const allLights = ref([])
const sensorMap = reactive({})
const cumulativeEnergyMap = reactive({})
const sensorApiAvailable = ref(null)

/** 是否使用批量传感器API（/latest/all — Redis缓存版） */
const useBatchSensorApi = ref(false)

const filter = reactive({ district: undefined, road: undefined, status: undefined })
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
    return []
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
    if (!l.road) return
    if (filter.district && l.district !== filter.district) return
    roads.add(l.road)
  })
  const arr = Array.from(roads)
  if (arr.length === 0) {
    return []
  }
  return arr.map((r) => ({ value: r, label: r }))
})

const stats = computed(() => {
  const online = allLights.value.filter((l) => l.status === 1).length
  const fault = allLights.value.filter((l) => l.status === 2).length
  const offline = allLights.value.filter((l) => l.status === 0).length
  return { online, fault, offline }
})

const lightsWithLocation = computed(() =>
  allLights.value.filter(
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
    voltage: d.voltage,
    current: d.current,
    temperature: d.temperature,
    humidity: d.humidity,
    samplingEnergy: d.samplingEnergy,
    collectTime: d.collectTime
  }
}

/**
 * 加载传感器数据 — 优先使用批量接口（Redis 缓存版）
 *
 * 优化后：一次 GET /api/sensor-data/latest/all 获取所有路灯最新数据
 * 降级方案：如果批量接口不可用，回退到逐盏查询
 */
async function loadSensorData(lights) {
  if (!lights || lights.length === 0) return
  if (sensorApiAvailable.value === false) return

  // ===== 优先尝试批量接口（Redis pipeline，一次返回全部） =====
  try {
    const res = await getAllLatestSensorData()
    // 请求成功 -> 全部数据从 Redis 拿到
    sensorApiAvailable.value = true
    useBatchSensorApi.value = true

    const dataMap = res.data || {}
    // 清空旧数据
    Object.keys(sensorMap).forEach(k => delete sensorMap[k])
    // 填入批量结果
    Object.entries(dataMap).forEach(([lightId, data]) => {
      applySensor(Number(lightId), data)
    })
    return
  } catch (e) {
    // 批量接口不可用（如 Redis 未部署），降级
    if (useBatchSensorApi.value !== true) {
      console.warn('[sensor] 批量接口不可用，降级为逐盏查询', e.message)
    }
    useBatchSensorApi.value = false
  }

  // ===== 降级方案：逐盏查询（保持向后兼容） =====
  if (sensorApiAvailable.value === null) {
    try {
      const res = await getLatestSensorData(lights[0].id)
      sensorApiAvailable.value = true
      applySensor(lights[0].id, res.data)
    } catch (e) {
      sensorApiAvailable.value = false
      return
    }
    // 并发 8 路查询剩余路灯
    const queue = lights.slice(1)
    const workers = Array.from({ length: Math.min(8, queue.length) }, () =>
      (async () => {
        while (queue.length) {
          const l = queue.shift()
          if (!l) break
          try {
            const res = await getLatestSensorData(l.id)
            applySensor(l.id, res.data)
          } catch (e) { /* 单盏查询失败跳过 */ }
        }
      })()
    )
    await Promise.all(workers)
  } else {
    const queue = [...lights]
    const workers = Array.from({ length: Math.min(8, queue.length) }, () =>
      (async () => {
        while (queue.length) {
          const l = queue.shift()
          if (!l) break
          try {
            const res = await getLatestSensorData(l.id)
            applySensor(l.id, res.data)
          } catch (e) { /* 单盏查询失败跳过 */ }
        }
      })()
    )
    await Promise.all(workers)
  }
}

const mapContainer = ref(null)
let mapInstance = null
let tileLayer = null
let markersLayer = null
let scaleControl = null
const markerMap = reactive({})

const mapZoom = ref(13)
const mapCenter = ref([31.2304, 121.4737])
const currentZoom = ref(13)
const scaleText = ref('0 m')

const gaodeUrl = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const gaodeSubdomains = ['1', '2', '3', '4']

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

  Object.keys(markerMap).forEach((key) => delete markerMap[key])

  mapInstance = L.map(mapContainer.value, {
    center: mapCenter.value,
    zoom: mapZoom.value,
    zoomControl: true,
    attributionControl: false
  })

  markersLayer = L.featureGroup().addTo(mapInstance)
  addTileLayer()

  if (scaleControl) {
    mapInstance.removeControl(scaleControl)
  }
  scaleControl = L.control.scale({
    position: 'bottomleft',
    imperial: false,
    metric: true,
    maxWidth: 120
  }).addTo(mapInstance)

  mapInstance.on('zoomend', () => {
    if (!mapInstance) return
    const z = mapInstance.getZoom()
    currentZoom.value = z
    scaleText.value = computeScaleText(z)
  })
  currentZoom.value = mapInstance.getZoom()
  scaleText.value = computeScaleText(mapInstance.getZoom())
}

function computeScaleText(zoom) {
  const metersPerPixel = 156543.03392 * Math.cos((31.23 * Math.PI) / 180) / Math.pow(2, zoom)
  const meters = Math.round(metersPerPixel * 100)
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

async function onMarkerClick(light) {
  selectedLight.value = light
  detailDialog.value = true
  // 弹窗打开时立即获取最新详情（含 brightness 等字段）
  try {
    const res = await getLightById(light.id)
    if (res.data) {
      selectedLight.value = { ...light, ...res.data }
    }
  } catch (e) {}
  loadSensorData([selectedLight.value || light])
}

function goToDetail() {
  if (!selectedLight.value) return
  const id = selectedLight.value.id
  detailDialog.value = false
  router.push({ path: `/devices/${id}`, query: { from: 'monitor' } })
}

// ====== 跨页面同步：通过 BroadcastChannel 通知其他页面/标签页刷新 ======
const syncChannel = typeof BroadcastChannel !== 'undefined'
  ? new BroadcastChannel('smartlight_light_detail')
  : null

function broadcastLightUpdate(lightId) {
  if (syncChannel) {
    try {
      syncChannel.postMessage({ type: 'light:updated', lightId, ts: Date.now() })
    } catch (e) {}
  }
}

async function refreshAll(reprobe = false) {
  if (reprobe) sensorApiAvailable.value = null
  loading.value = true
  try {
    const [pageRes, listRes] = await Promise.all([
      getLightPage({
        pageNum: listQuery.pageNum,
        pageSize: listQuery.pageSize,
        district: filter.district,
        road: filter.road,
        status: filter.status
      }),
      getAllLights()
    ])
    const newPage = pageRes.data || { records: [], total: 0 }
    const newAll = listRes.data || []
    // 检测列表中状态是否有变化（用于跨标签/页面同步通知）
    const prevStatusMap = new Map(allLights.value.map((l) => [l.id, l.status]))
    page.value = newPage
    allLights.value = newAll
    await loadSensorData(allLights.value)
    // 获取今日累计耗电（独立于传感器采样间隔耗电）
    try {
      const energyRes = await getTodayEnergy()
      if (energyRes.data) {
        Object.keys(cumulativeEnergyMap).forEach(k => delete cumulativeEnergyMap[k])
        Object.entries(energyRes.data).forEach(([lightId, energy]) => {
          cumulativeEnergyMap[lightId] = energy
        })
      }
    } catch (e) { /* 累计耗电接口失败不影响主流程 */ }
    if (mode.value === 'map') {
      await nextTick()
      renderMarkers()
    }
    // 状态有变化的设备广播更新
    allLights.value.forEach((l) => {
      const prev = prevStatusMap.get(l.id)
      if (prev !== undefined && prev !== l.status) {
        broadcastLightUpdate(l.id)
      }
    })
    // 若弹窗打开，同步更新弹窗内选中的路灯数据（并获取最新详情含 brightness）
    if (detailDialog.value && selectedLight.value) {
      const updated = allLights.value.find((l) => l.id === selectedLight.value.id)
      if (updated) {
        selectedLight.value = updated
        loadSensorData([updated])
        // 额外获取最新详情确保 brightness 等字段同步
        try {
          const detailRes = await getLightById(updated.id)
          if (detailRes.data) {
            selectedLight.value = { ...updated, ...detailRes.data }
          }
        } catch (e) {}
      }
    }
  } catch (e) {
    page.value = { records: [], total: 0 }
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
  refreshAll(false)
}

function onFilterChange(changedField) {
  if (changedField === 'district' && filter.road && filter.district) {
    const districts = roadDistrictsMap.value.get(filter.road)
    if (districts && !districts.has(filter.district)) {
      filter.road = undefined
    }
  }
  applyFilter()
}

function onDistrictChange(v) {
  if (filter.road) {
    const districts = roadDistrictsMap.value.get(filter.road)
    if (!districts || !districts.has(v)) {
      filter.road = undefined
    }
  }
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

  if (filter.district && districts?.has(filter.district)) {
    applyFilter()
    return
  }

  if (districts && districts.size === 1) {
    filter.district = Array.from(districts)[0]
    applyFilter()
    return
  }

  if (districts && districts.size > 1) {
    filter.district = undefined
    applyFilter()
    openDistrictSelect(road, districts)
    return
  }

  applyFilter()
}

let timer = null
let isRefreshing = false

function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  scheduleNext()
}

function scheduleNext() {
  timer = setTimeout(async () => {
    if (!autoRefresh.value) return
    if (isRefreshing) {
      scheduleNext()
      return
    }
    try {
      isRefreshing = true
      await refreshAll(false)
    } catch (e) {} finally {
      isRefreshing = false
    }
    if (autoRefresh.value) scheduleNext()
  }, interval.value * 1000)
}

function stopPolling() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  isRefreshing = false
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
    refreshAll(false)
  }
)

watch(mode, async (m) => {
  if (m === 'map') {
    await nextTick()
    initMap()
    renderMarkers()
    setTimeout(() => {
      if (mapInstance) {
        mapInstance.invalidateSize()
        updateMapBounds()
      }
    }, 100)
  }
})

watch(() => appStore.lightDataVersion, () => {
  refreshAll(false)
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
  // 监听其他标签/页面发来的更新广播，触发本页面同步刷新
  if (syncChannel) {
    syncChannel.addEventListener('message', (e) => {
      const msg = e.data
      if (msg && msg.type === 'light:updated') {
        refreshAll(false)
      }
    })
  }
})

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('resize', onResize)
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
  }
  if (syncChannel) {
    syncChannel.close()
  }
})
</script>

<style scoped>
.map-card {
  max-height: 560px;
  height: calc(100vh - 380px);
  min-height: 300px;
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
}
</style>