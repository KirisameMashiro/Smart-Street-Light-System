<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">远程控制</h2>
      <div class="toolbar">
        <el-button
          type="success"
          :icon="Switch"
          :disabled="selection.length === 0"
          @click="onBatchSwitch(1)"
        >批量开灯</el-button>
        <el-button
          type="info"
          :icon="SwitchButton"
          :disabled="selection.length === 0"
          @click="onBatchSwitch(0)"
        >批量关灯</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 控制面板 -->
    <div class="control-panel">
      <!-- 单灯调光：仅选中单灯时显示 -->
      <el-card v-if="selection.length === 1" shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <el-icon><Sunny /></el-icon>
            <span>单灯调光 — {{ selection[0].lightCode }} {{ selection[0].lightName }}</span>
          </div>
        </template>
        <div class="dimming-row">
          <span class="dim-label">亮度</span>
          <el-slider
            v-model="dimBrightness"
            :min="0"
            :max="100"
            :step="1"
            style="flex: 1; margin: 0 16px"
          />
          <span class="dim-value">{{ dimBrightness }}%</span>
          <el-button type="primary" :loading="dimming" @click="onApplyDimming">下发亮度</el-button>
        </div>
      </el-card>

      <!-- 分组操作 -->
      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <el-icon><Operation /></el-icon>
            <span>分组操作</span>
            <el-select
              v-model="groupBy"
              size="small"
              style="width: 140px; margin-left: 12px"
            >
              <el-option
                v-for="o in GROUP_BY_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </div>
        </template>
        <div v-if="groups.length === 0" class="text-muted">暂无可分组设备</div>
        <div v-else class="group-list">
          <div v-for="g in groups" :key="g.key" class="group-item">
            <div class="group-info">
              <span class="group-name">{{ g.label }}</span>
              <el-tag size="small" type="info">{{ g.count }} 盏</el-tag>
            </div>
            <div class="group-ops">
              <el-button size="small" type="success" @click="onGroupSwitch(g, 1)">全部开灯</el-button>
              <el-button size="small" type="info" @click="onGroupSwitch(g, 0)">全部关灯</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select
        v-model="query.district"
        placeholder="行政区"
        clearable
        style="width: 150px"
      >
        <el-option v-for="o in DISTRICT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select
        v-model="query.road"
        placeholder="路段"
        clearable
        style="width: 150px"
      >
        <el-option v-for="o in ROAD_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select
        v-model="query.deviceType"
        placeholder="设备类型"
        clearable
        style="width: 160px"
      >
        <el-option v-for="o in DEVICE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select
        v-model="query.status"
        placeholder="状态"
        clearable
        style="width: 140px"
      >
        <el-option
          v-for="(item, key) in LIGHT_STATUS_MAP"
          :key="key"
          :label="item.label"
          :value="Number(key)"
        />
      </el-select>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 设备列表 -->
    <div class="table-card">
      <el-table
        ref="tableRef"
        :data="filteredData"
        v-loading="loading"
        stripe
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="lightCode" label="编号" width="120" />
        <el-table-column prop="lightName" label="名称" width="130" show-overflow-tooltip />
        <el-table-column prop="district" label="行政区" width="100" />
        <el-table-column prop="road" label="路段" width="100" />
        <el-table-column prop="deviceType" label="设备类型" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="LIGHT_STATUS_MAP[row.status]?.type" size="small">
              {{ LIGHT_STATUS_MAP[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="亮度" width="90">
          <template #default="{ row }">{{ row.brightness ?? 0 }}%</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" class-name="table-ops">
          <template #default="{ row }">
            <el-button link type="success" :disabled="row.status === 2" @click="onSingleSwitch(row, 1)">开灯</el-button>
            <el-button link type="info" :disabled="row.status === 2" @click="onSingleSwitch(row, 0)">关灯</el-button>
            <el-button link type="primary" @click="onSelectSingle(row)">调光</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Switch,
  SwitchButton,
  Refresh,
  RefreshLeft,
  Sunny,
  Operation
} from '@element-plus/icons-vue'
import { getAllLights, batchSwitchLight, setLightBrightness } from '@/api/light'
import {
  DISTRICT_OPTIONS,
  ROAD_OPTIONS,
  DEVICE_TYPE_OPTIONS,
  GROUP_BY_OPTIONS,
  LIGHT_STATUS_MAP
} from '@/utils/constants'
import { logOperation } from '@/utils/log'

const loading = ref(false)
const dimming = ref(false)
const allData = ref([])
const selection = ref([])
const groupBy = ref('district')
const dimBrightness = ref(0)
const tableRef = ref()

const query = reactive({
  district: undefined,
  road: undefined,
  deviceType: undefined,
  status: undefined
})

// 本地筛选（getAllLights 一次性拉取）
const filteredData = computed(() => {
  return allData.value.filter((d) => {
    if (query.district && d.district !== query.district) return false
    if (query.road && d.road !== query.road) return false
    if (query.deviceType && d.deviceType !== query.deviceType) return false
    if (query.status !== undefined && query.status !== null && d.status !== query.status) return false
    return true
  })
})

// 分组：按所选维度聚合当前筛选结果
const groups = computed(() => {
  const map = new Map()
  for (const d of filteredData.value) {
    const key = d[groupBy.value] || '未分组'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(d)
  }
  return Array.from(map.entries()).map(([key, list]) => ({
    key,
    label: key,
    count: list.length,
    ids: list.map((x) => x.id)
  }))
})

// 选中单灯时回填亮度滑块
watch(selection, (val) => {
  if (val.length === 1) {
    dimBrightness.value = val[0].brightness ?? 0
  }
})

async function loadData() {
  loading.value = true
  try {
    const res = await getAllLights()
    allData.value = res.data || []
  } catch (e) {
    allData.value = []
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selection.value = rows
}

function onReset() {
  query.district = undefined
  query.road = undefined
  query.deviceType = undefined
  query.status = undefined
}

// 通过“调光”按钮选中单灯
function onSelectSingle(row) {
  tableRef.value?.clearSelection()
  tableRef.value?.toggleRowSelection(row, true)
}

// 批量开关灯（基于勾选）
async function onBatchSwitch(status) {
  const validIds = selection.value.filter((r) => r.status !== 2).map((r) => r.id)
  const faultCount = selection.value.filter((r) => r.status === 2).length
  if (!validIds.length) {
    if (faultCount > 0) {
      ElMessage.warning(`选中的 ${selection.value.length} 盏路灯均为故障状态，无法进行开关操作`)
    }
    return
  }
  const text = status === 1 ? '开启' : '关闭'
  const confirmText = faultCount > 0
    ? `确定要${text}选中的 ${validIds.length} 盏路灯吗？（${faultCount} 盏故障路灯将被跳过）`
    : `确定要${text}选中的 ${validIds.length} 盏路灯吗？`
  try {
    await ElMessageBox.confirm(confirmText, '批量操作', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await batchSwitchLight(validIds, status)
    const message = faultCount > 0
      ? `已${text} ${validIds.length} 盏路灯（${faultCount} 盏故障路灯已跳过）`
      : `已${text} ${validIds.length} 盏路灯`
    ElMessage.success(message)
    await logOperation('batch_switch', `批量${text} ${validIds.length} 盏路灯`, '成功')
    loadData()
  } catch (e) {
    await logOperation('batch_switch', `批量${text} ${validIds.length} 盏路灯`, '失败')
  }
}

// 分组批量开关
async function onGroupSwitch(group, status) {
  const groupData = filteredData.value.filter((d) => d[groupBy.value] === group.key)
  const validIds = groupData.filter((d) => d.status !== 2).map((d) => d.id)
  const faultCount = groupData.filter((d) => d.status === 2).length
  if (!validIds.length) {
    if (faultCount > 0) {
      ElMessage.warning(`「${group.label}」分组下的 ${group.count} 盏路灯均为故障状态，无法进行开关操作`)
    }
    return
  }
  const text = status === 1 ? '开启' : '关闭'
  const confirmText = faultCount > 0
    ? `确定要${text}「${group.label}」分组下的 ${validIds.length} 盏路灯吗？（${faultCount} 盏故障路灯将被跳过）`
    : `确定要${text}「${group.label}」分组下的 ${validIds.length} 盏路灯吗？`
  try {
    await ElMessageBox.confirm(confirmText, '分组操作', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await batchSwitchLight(validIds, status)
    const message = faultCount > 0
      ? `已${text}「${group.label}」 ${validIds.length} 盏路灯（${faultCount} 盏故障路灯已跳过）`
      : `已${text}「${group.label}」 ${validIds.length} 盏路灯`
    ElMessage.success(message)
    await logOperation('batch_switch', `分组${text}「${group.label}」${validIds.length} 盏路灯`, '成功')
    loadData()
  } catch (e) {
    await logOperation('batch_switch', `分组${text}「${group.label}」${validIds.length} 盏路灯`, '失败')
  }
}

// 单灯开关
async function onSingleSwitch(row, status) {
  const text = status === 1 ? '开启' : '关闭'
  const name = row.lightName || row.lightCode
  try {
    await batchSwitchLight([row.id], status)
    ElMessage.success(`已${text}「${name}」`)
    await logOperation(status === 1 ? 'switch_on' : 'switch_off', `${text}「${name}」`, '成功')
    loadData()
  } catch (e) {
    await logOperation(status === 1 ? 'switch_on' : 'switch_off', `${text}「${name}」`, '失败')
  }
}

// 下发亮度
async function onApplyDimming() {
  if (selection.value.length !== 1) return
  const row = selection.value[0]
  const name = row.lightName || row.lightCode
  dimming.value = true
  try {
    await setLightBrightness(row.id, dimBrightness.value)
    ElMessage.success(`已设置「${name}」亮度为 ${dimBrightness.value}%`)
    await logOperation('dimming', `设置「${name}」亮度为 ${dimBrightness.value}%`, '成功')
    loadData()
  } catch (e) {
    await logOperation('dimming', `设置「${name}」亮度为 ${dimBrightness.value}%`, '失败')
  } finally {
    dimming.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.control-panel {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.panel-card {
  flex: 1;
  min-width: 320px;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dimming-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.dim-label {
  color: #606266;
}
.dim-value {
  width: 44px;
  text-align: right;
  font-weight: 600;
}
.group-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
}
.group-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.group-name {
  font-weight: 500;
}
</style>
