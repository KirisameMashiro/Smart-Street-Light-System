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
                :type="LIGHT_STATUS_MAP[Number(row.status)]?.type"
                size="small"
                :effect="Number(row.status) === 2 ? 'dark' : 'light'"
              >
                {{ LIGHT_STATUS_MAP[Number(row.status)]?.label }}
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
          <span class="map-info-item">比例: <b>{{ scaleRatio }}</b></span>
        </span>
        <el-input-number
          :model-value="currentZoom"
          :min="3"
          :max="18"
          :step="0.5"
          size="small"
          :precision="1"
          style="width: 110px"
          @change="onZoomChange"
        />
        <el-button size="small" @click="setZoom(16)">1:4m</el-button>
        <el-button size="small" @click="setZoom(18)">1:1m</el-button>
        <el-select
          v-model="selectedZone"
          placeholder="选择路灯"
          clearable
          filterable
          style="width: 160px"
          size="small"
          @change="onZoneChange"
        >
          <el-option
            v-for="l in lightsWithLocation"
            :key="l.id"
            :label="(l.lightName || l.lightCode) + ' (' + l.lightCode + ')'"
            :value="l.id"
          />
        </el-select>
        <el-button type="primary" size="small" :icon="Plus" @click="openAddLightDialog">添加路灯</el-button>
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

    <!-- 添加路灯弹窗 -->
    <el-dialog v-model="addLightDialog" title="添加路灯" width="480px" :close-on-click-modal="false">
      <el-form ref="addLightFormRef" :model="addLightForm" label-width="100px">
        <el-form-item label="设备编号" required>
          <el-input v-model="addLightForm.lightCode" placeholder="如 L-CQ-001" />
        </el-form-item>
        <el-form-item label="设备名称" required>
          <el-input v-model="addLightForm.lightName" placeholder="如 熊猫馆路灯001" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="addLightForm.location" placeholder="如 熊猫馆门前" />
        </el-form-item>
        <el-form-item label="行政区">
          <el-select v-model="addLightForm.district" placeholder="选择行政区（可搜索动物园园区）" filterable allow-create>
            <el-option v-for="o in availableDistricts" :key="o.value" :label="o.label" :value="o.value">
              <span>{{ o.label }}</span>
              <span v-if="o.isZooZone" style="float:right; font-size:11px; color:#67c23a; margin-left:8px">🦁 园区</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="路段">
          <el-select v-model="addLightForm.road" placeholder="选择路段">
            <el-option v-for="o in availableRoads" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型">
          <el-input v-model="addLightForm.deviceType" placeholder="如 LED路灯" />
        </el-form-item>
        <el-form-item label="额定功率(W)">
          <el-input-number v-model="addLightForm.ratedPower" :min="0" :step="1" placeholder="0" />
        </el-form-item>
        <el-form-item label="经纬度">
          <div class="coord-display">
            <span>经度: <b>{{ addLightForm.longitude || '未选择' }}</b></span>
            <span>纬度: <b>{{ addLightForm.latitude || '未选择' }}</b></span>
            <el-button size="small" type="info" @click="pickLocationOnMap">在地图上选择位置</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addLightDialog = false">取消</el-button>
        <el-button type="primary" :loading="addingLight" @click="onAddLight">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'RealtimeMonitor' })
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Plus } from '@element-plus/icons-vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getLightPage, getLightById, addLight } from '@/api/light'
import { getAllLatestSensorData, getLatestSensorData, getTodayEnergy } from '@/api/sensor'
import { getDistricts } from '@/api/config'
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
const systemDistricts = ref([])
const sensorMap = reactive({})
const cumulativeEnergyMap = reactive({})
const sensorApiAvailable = ref(null)

/** 是否使用批量传感器API（/latest/all — Redis缓存版） */
const useBatchSensorApi = ref(false)

const filter = reactive({ district: undefined, status: undefined })
const listQuery = reactive({ pageNum: 1, pageSize: 10 })

const availableDistricts = computed(() => {
  const districts = new Set()
  // 优先加入数据库中已存在的行政区（区域管理表）
  systemDistricts.value.forEach((d) => {
    if (d.districtName) districts.add(d.districtName)
  })
  // 补充路灯数据中已使用的行政区
  allLights.value.forEach((l) => {
    if (l.district) districts.add(l.district)
  })
  // 补充重庆动物园园区名称作为可选行政区
  zooZones.forEach((z) => {
    if (z.name) districts.add(z.name)
  })
  const arr = Array.from(districts)
  if (arr.length === 0) {
    return []
  }
  return arr.map((d) => ({
    value: d,
    label: d,
    isZooZone: zooZones.some((z) => z.name === d)
  }))
})

const stats = computed(() => {
  const online = allLights.value.filter((l) => Number(l.status) === 1).length
  const fault = allLights.value.filter((l) => Number(l.status) === 2).length
  const offline = allLights.value.filter((l) => Number(l.status) === 0).length
  return { online, fault, offline }
})

const lightsWithLocation = computed(() =>
  allLights.value.filter((l) => {
    if (l.longitude == null || l.latitude == null || l.longitude === 0 || l.latitude === 0) {
      return false
    }
    if (filter.district && l.district !== filter.district) {
      return false
    }
    return true
  })
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
let markerMap = {}

const mapZoom = ref(14)
const mapCenter = ref([29.4253, 106.4978])
const currentZoom = ref(14)
const scaleText = ref('0 m')
const scaleRatio = ref('1:100')

const MAP_STATE_KEY = 'smartlight_map_state'

function saveMapState() {
  if (!mapInstance) return
  const center = mapInstance.getCenter()
  const zoom = mapInstance.getZoom()
  localStorage.setItem(MAP_STATE_KEY, JSON.stringify({
    lat: center.lat,
    lng: center.lng,
    zoom
  }))
}

function loadMapState() {
  try {
    const saved = localStorage.getItem(MAP_STATE_KEY)
    if (saved) {
      const state = JSON.parse(saved)
      if (state.lat != null && state.lng != null) {
        mapCenter.value = [state.lat, state.lng]
      }
      if (state.zoom != null) {
        mapZoom.value = state.zoom
      }
    }
  } catch (e) {
    // ignore
  }
}

const gaodeUrl = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const gaodeSubdomains = ['1', '2', '3', '4']

const zooZones = [
  { name: '熊猫馆', lat: 29.4258, lng: 106.4975, zoom: 17 },
  { name: '大象馆', lat: 29.4265, lng: 106.4982, zoom: 17 },
  { name: '长颈鹿馆', lat: 29.4262, lng: 106.4978, zoom: 17 },
  { name: '老虎馆', lat: 29.4255, lng: 106.4985, zoom: 17 },
  { name: '狮子馆', lat: 29.4252, lng: 106.4980, zoom: 17 },
  { name: '猴山', lat: 29.4268, lng: 106.4972, zoom: 17 },
  { name: '鸟语林', lat: 29.4270, lng: 106.4976, zoom: 17 },
  { name: '两栖爬行动物馆', lat: 29.4250, lng: 106.4970, zoom: 17 },
  { name: '企鹅馆', lat: 29.4248, lng: 106.4988, zoom: 17 },
  { name: '金丝猴馆', lat: 29.4260, lng: 106.4984, zoom: 17 },
  { name: '儿童乐园', lat: 29.4245, lng: 106.4965, zoom: 17 },
  { name: '正门广场', lat: 29.4235, lng: 106.4960, zoom: 17 },
  { name: '南门入口', lat: 29.4275, lng: 106.4968, zoom: 17 },
  { name: '北门入口', lat: 29.4230, lng: 106.4985, zoom: 17 },
  { name: '天鹅湖', lat: 29.4272, lng: 106.4990, zoom: 17 },
  { name: '停车场', lat: 29.4232, lng: 106.4955, zoom: 17 }
]

const selectedZone = ref('')

function setZoom(zoom) {
  if (!mapInstance) return
  mapInstance.setZoom(zoom, { animate: true })
}

function onZoomChange(val) {
  if (!mapInstance || val == null) return
  mapInstance.setZoom(Number(val), { animate: true })
}

function onZoneChange(lightId) {
  if (lightId == null || lightId === '' || !mapInstance) return
  const light = allLights.value.find(
    (l) => l.id === lightId || l.id === Number(lightId)
  )
  if (!light || light.longitude == null || light.latitude == null) {
    ElMessage.warning('该路灯暂无位置信息')
    return
  }
  // 使用 panTo 将路灯移动到地图中央，保持当前缩放级别不变
  mapInstance.panTo([Number(light.latitude), Number(light.longitude)], { animate: true })
  const name = light.lightName || light.lightCode || `路灯#${light.id}`
  ElMessage.success(`已定位到${name}`)
}

function getMarkerColor(status) {
  const s = Number(status)
  if (s === 1) return '#67c23a'
  if (s === 2) return '#f56c6c'
  return '#909399'
}

function getStatusIcon(status) {
  const s = Number(status)
  if (s === 1) return '◎'
  if (s === 2) return '✕'
  return '○'
}

function getMarkerClass(status) {
  const s = Number(status)
  if (s === 1) return 'marker-online'
  if (s === 2) return 'marker-fault'
  return 'marker-offline'
}

function createMarkerIcon(light) {
  const s = Number(light.status)
  const color = getMarkerColor(s)
  const iconHtml = `<div class="light-marker ${getMarkerClass(s)}">
    ${s === 1 ? `<div class="marker-glow" style="background-color: ${color}"></div>` : ''}
    ${s === 1 ? `<div class="marker-glow-ring" style="border-color: ${color}"></div>` : ''}
    <div class="marker-outer">
      <div class="marker-inner" style="background-color: ${color}"></div>
    </div>
    ${s === 2 ? '<div class="marker-pulse"></div>' : ''}
    ${s === 2 ? '<div class="marker-pulse-ring"></div>' : ''}
    <div class="marker-label">${light.lightCode ? light.lightCode.substring(light.lightCode.length - 3) : light.id}</div>
  </div>`

  return L.divIcon({
    html: iconHtml,
    className: '',
    iconSize: [56, 64],
    iconAnchor: [28, 56],
    popupAnchor: [0, -40]
  })
}

function initMap() {
  if (!mapContainer.value) return

  loadMapState()

  if (mapInstance) {
    nextTick(() => {
      mapInstance.invalidateSize()
    })
    return
  }

  markerMap = {}

  mapInstance = L.map(mapContainer.value, {
    center: mapCenter.value,
    zoom: mapZoom.value,
    zoomControl: true,
    attributionControl: false,
    minZoom: 3,
    maxZoom: 18,
    zoomSnap: 0.5,
    zoomDelta: 0.5
  })

  markersLayer = L.featureGroup().addTo(mapInstance)
  addTileLayer()

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
    scaleRatio.value = computeScaleRatio(z)
    saveMapState()
  })

  mapInstance.on('moveend', () => {
    saveMapState()
  })

  mapInstance.on('click', (e) => {
    handleMapClick(e)
  })

  currentZoom.value = mapInstance.getZoom()
  scaleText.value = computeScaleText(mapInstance.getZoom())
  scaleRatio.value = computeScaleRatio(mapInstance.getZoom())
}

function computeScaleText(zoom) {
  const metersPerPixel = 156543.03392 * Math.cos((29.4253 * Math.PI) / 180) / Math.pow(2, zoom)
  const centimeters = metersPerPixel * 100 * 100
  if (centimeters >= 100000) {
    const km = centimeters / 100000
    return km.toFixed(km >= 5 ? 0 : 1) + ' km'
  } else if (centimeters >= 1000) {
    return Math.round(centimeters / 100) / 10 + ' m'
  } else if (centimeters >= 10) {
    return Math.round(centimeters) + ' cm'
  } else if (centimeters >= 1) {
    return centimeters.toFixed(1) + ' cm'
  } else {
    const mm = centimeters * 10
    return mm.toFixed(1) + ' mm'
  }
}

function computeScaleRatio(zoom) {
  const metersPerPixel = 156543.03392 * Math.cos((29.4253 * Math.PI) / 180) / Math.pow(2, zoom)
  const pixelsPerMeter = 1 / metersPerPixel
  const scale = Math.round(pixelsPerMeter)
  if (scale <= 0) return '1:1'
  return `1:${scale}`
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

  markersLayer.clearLayers()
  markerMap = {}

  lightsWithLocation.value.forEach((light) => {
    const icon = createMarkerIcon(light)
    const marker = L.marker(
      [Number(light.latitude), Number(light.longitude)],
      { icon }
    ).addTo(markersLayer)

    marker.on('click', () => {
      onMarkerClick(light)
    })

    markerMap[light.id] = marker
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
        mapInstance.setView([ll.lat, ll.lng], 19, { animate: true })
      } else {
        mapInstance.fitBounds(bounds, { padding: [60, 60], maxZoom: 20, minZoom: 12 })
      }
    }
  } catch (e) {
    if (layers[0] && layers[0].getLatLng()) {
      mapInstance.setView(layers[0].getLatLng(), 15)
    }
  }
}

const detailDialog = ref(false)
const selectedLight = ref(null)

const addLightDialog = ref(false)
const addLightFormRef = ref(null)
const addingLight = ref(false)
const pickingLocation = ref(false)
const addLightForm = reactive({
  lightCode: '',
  lightName: '',
  location: '',
  district: '',
  road: '',
  deviceType: '',
  ratedPower: 0,
  longitude: '',
  latitude: ''
})

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

function openAddLightDialog() {
  addLightDialog.value = true
  Object.assign(addLightForm, {
    lightCode: '',
    lightName: '',
    location: '',
    district: '',
    road: '',
    deviceType: '',
    ratedPower: 0,
    longitude: '',
    latitude: ''
  })
}

function pickLocationOnMap() {
  addLightDialog.value = false
  pickingLocation.value = true
  ElMessage.success('请在地图上点击选择路灯安装位置')
}

function handleMapClick(e) {
  if (!pickingLocation.value) return
  const lat = e.latlng.lat
  const lng = e.latlng.lng
  addLightForm.longitude = lng.toFixed(6)
  addLightForm.latitude = lat.toFixed(6)
  pickingLocation.value = false
  addLightDialog.value = true
  ElMessage.success(`已选择位置：经度 ${lng.toFixed(6)}，纬度 ${lat.toFixed(6)}`)
}

async function onAddLight() {
  if (!addLightForm.lightCode.trim()) {
    ElMessage.warning('请输入设备编号')
    return
  }
  if (!addLightForm.lightName.trim()) {
    ElMessage.warning('请输入设备名称')
    return
  }
  if (!addLightForm.longitude || !addLightForm.latitude) {
    ElMessage.warning('请选择经纬度位置')
    return
  }

  addingLight.value = true
  try {
    const payload = {
      lightCode: addLightForm.lightCode.trim(),
      lightName: addLightForm.lightName.trim(),
      location: addLightForm.location.trim(),
      district: addLightForm.district,
      road: addLightForm.road,
      deviceType: addLightForm.deviceType.trim(),
      ratedPower: addLightForm.ratedPower || 0,
      longitude: parseFloat(addLightForm.longitude),
      latitude: parseFloat(addLightForm.latitude),
      status: 0,
      brightness: 0,
      manualControl: 0
    }
    await addLight(payload)
    ElMessage.success('添加成功')
    addLightDialog.value = false
    await nextTick()
    refreshAll(true)
  } catch (error) {
    const raw = error.response?.data?.message || ''
    let msg = '添加失败'
    if (raw.includes('Duplicate entry') && raw.includes('uk_light_code')) {
      msg = '设备编号已存在，请更换后重试'
    } else if (raw.includes('Duplicate entry')) {
      msg = '数据重复，请检查编号或名称是否已存在'
    } else if (raw) {
      msg = raw
    }
    ElMessage.error(msg)
  } finally {
    addingLight.value = false
  }
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
    const [pageRes, allRes, districtRes] = await Promise.all([
      getLightPage({
        pageNum: listQuery.pageNum,
        pageSize: listQuery.pageSize,
        district: filter.district,
        status: filter.status
      }),
      getLightPage({ pageNum: 1, pageSize: 9999 }),
      getDistricts()
    ])
    const newPage = pageRes.data || { records: [], total: 0 }
    const newAll = allRes.data?.records || []
    systemDistricts.value = districtRes.data || []
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
  applyFilter()
}

function onDistrictChange(v) {
  onFilterChange('district', v)
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
  height: 520px;
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
  min-height: 250px;
}

.coord-display {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}

.coord-display b {
  color: #409eff;
}
</style>