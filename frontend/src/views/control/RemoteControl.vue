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
        <el-button
          type="warning"
          :disabled="selection.length === 0"
          @click="onBatchReleaseManual"
        >批量取消手动</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 控制面板 -->
    <div class="control-panel">
      <!-- 分组操作 -->
      <el-card shadow="never" class="panel-card full-width">
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
              <el-tag v-if="g.faultCount > 0" size="small" type="danger">{{ g.faultCount }} 盏故障</el-tag>
            </div>
            <div class="group-ops">
              <el-button
                size="small"
                type="success"
                :disabled="g.faultCount === g.count"
                @click="onGroupSwitch(g, 1)"
              >全部开灯</el-button>
              <el-button
                size="small"
                type="info"
                :disabled="g.faultCount === g.count"
                @click="onGroupSwitch(g, 0)"
              >全部关灯</el-button>
              <el-button
                size="small"
                type="warning"
                :disabled="g.faultCount === g.count"
                @click="onGroupReleaseManual(g)"
              >全部取消手动</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select
        v-model="query.district"
        placeholder="动物园区"
        clearable
        style="width: 150px"
        @change="onDistrictChange"
      >
        <el-option v-for="o in districtOptions" :key="o.value" :label="o.label" :value="o.value" />
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
        stripe
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="lightCode" label="编号" width="120" />
        <el-table-column prop="lightName" label="名称" width="130" show-overflow-tooltip />
        <el-table-column prop="district" label="动物园区" width="100" />
        <el-table-column prop="road" label="路段" width="100" />
        <el-table-column prop="deviceType" label="设备类型" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="LIGHT_STATUS_MAP[row.status]?.type" size="small">
              {{ LIGHT_STATUS_MAP[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="亮度" width="130">
          <template #default="{ row }">
            <div class="brightness-cell">
              <span>{{ row.brightness ?? 0 }}%</span>
              <el-tag
                :type="row.manualControl ? 'warning' : 'success'"
                size="small"
                effect="plain"
                style="margin-left: 6px"
              >
                {{ row.manualControl ? '手动' : '自动' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" class-name="table-ops">
          <template #default="{ row }">
            <el-button link type="success" :disabled="row.status === 2" @click="onSingleSwitch(row, 1)">开灯</el-button>
            <el-button link type="info" :disabled="row.status === 2" @click="onSingleSwitch(row, 0)">关灯</el-button>
            <el-button link type="primary" :disabled="row.status === 2" @click="onSelectSingle(row)">调光</el-button>
            <el-button
              v-if="row.manualControl"
              link
              type="warning"
              :disabled="row.status === 2"
              @click="onReleaseManual(row)"
            >取消手动</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 调光弹窗 -->
    <el-dialog v-model="dimDialogVisible" title="单灯调光" width="480px">
      <div v-if="currentDimLight" class="dim-dialog-content">
        <div class="dim-light-info">
          <div class="dim-light-name">{{ currentDimLight.lightName || '-' }}</div>
          <div class="dim-light-code">{{ currentDimLight.lightCode }}</div>
          <div style="margin-top: 8px; display: flex; justify-content: center; gap: 8px">
            <el-tag
              :type="LIGHT_STATUS_MAP[currentDimLight.status]?.type"
              size="small"
            >
              {{ LIGHT_STATUS_MAP[currentDimLight.status]?.label }}
            </el-tag>
            <el-tag
              :type="currentDimLight.manualControl ? 'warning' : 'success'"
              size="small"
              effect="plain"
            >
              {{ currentDimLight.manualControl ? '手动控制' : '自动控制' }}
            </el-tag>
          </div>
        </div>
        <el-alert
          v-if="currentDimLight.status === 2"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          <template #title>该路灯处于故障状态，无法进行调光操作。请前往「故障处理」页面进行修复。</template>
        </el-alert>
        <el-alert
          v-else-if="!currentDimLight.manualControl"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          <template #title>当前为自动调光模式，亮度由定时策略或阈值联动自动调节。手动下发亮度后将切换为手动控制模式，定时策略将不再自动调节此路灯。</template>
        </el-alert>
        <el-alert
          v-else
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          <template #title>当前为手动控制模式，定时策略不会自动调节此路灯的亮度和开关状态。</template>
        </el-alert>
        <div class="dim-slider-row">
          <span class="dim-label">亮度</span>
          <el-slider
            v-model="dimBrightness"
            :min="0"
            :max="100"
            :step="1"
            :disabled="currentDimLight.status === 2"
            style="flex: 1; margin: 0 16px"
          />
          <span class="dim-value">{{ dimBrightness }}%</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="dimDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="dimming"
          :disabled="currentDimLight?.status === 2"
          @click="onApplyDimming"
        >下发亮度</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'RemoteControl' })
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
import { getAllLights, batchSwitchLight, setLightBrightness, releaseManualControl, releaseManualControlBatch } from '@/api/light'
import { getSystemDistricts, getSystemRoads } from '@/api/system'
import {
  DEVICE_TYPE_OPTIONS,
  GROUP_BY_OPTIONS,
  LIGHT_STATUS_MAP
} from '@/utils/constants'
import { logOperation } from '@/utils/log'
import { useAppStore } from '@/store/app'

const appStore = useAppStore()

const syncChannel = typeof BroadcastChannel !== 'undefined'
  ? new BroadcastChannel('smartlight_light_detail')
  : null

function broadcastLightUpdate() {
  if (syncChannel) {
    try {
      syncChannel.postMessage({ type: 'light:updated', ts: Date.now() })
    } catch (e) {}
  }
  appStore.notifyLightDataChanged()
}

const loading = ref(false)
const dimming = ref(false)
const allData = ref([])
const selection = ref([])
const groupBy = ref('district')
const dimBrightness = ref(0)
const tableRef = ref()
const dimDialogVisible = ref(false)
const currentDimLight = ref(null)

const districtOptions = ref([])
const roadOptions = ref([])
const districtRoadMap = ref({})
const roadDistrictMap = ref({})

const query = reactive({
  district: undefined,
  deviceType: undefined,
  status: undefined
})

// 本地筛选（getAllLights 一次性拉取）
const filteredData = computed(() => {
  return allData.value.filter((d) => {
    if (query.district && d.district !== query.district) return false
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
    faultCount: list.filter((x) => x.status === 2).length,
    ids: list.map((x) => x.id)
  }))
})



async function loadData() {
  loading.value = true
  try {
    const [lightsRes, districtsRes] = await Promise.all([
      getAllLights(),
      getSystemDistricts()
    ])
    allData.value = lightsRes.data || []
    districtOptions.value = (districtsRes.data || []).map(d => ({ value: d.districtName, label: d.districtName }))
  } catch (e) {
    allData.value = []
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selection.value = rows
}

function onDistrictChange() {
  query.pageNum = 1
}

function onReset() {
  query.district = undefined
  query.deviceType = undefined
  query.status = undefined
}

// 打开调光弹窗
function onSelectSingle(row) {
  currentDimLight.value = row
  dimBrightness.value = row.brightness ?? 0
  dimDialogVisible.value = true
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
    broadcastLightUpdate()
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
    broadcastLightUpdate()
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
    broadcastLightUpdate()
  } catch (e) {
    await logOperation(status === 1 ? 'switch_on' : 'switch_off', `${text}「${name}」`, '失败')
  }
}

// 取消手动控制
async function onReleaseManual(row) {
  const name = row.lightName || row.lightCode
  try {
    await releaseManualControl(row.id)
    ElMessage.success(`已取消「${name}」的手动控制，恢复自动控制`)
    await logOperation('release_manual', `取消「${name}」手动控制`, '成功')
    loadData()
    broadcastLightUpdate()
  } catch (e) {
    await logOperation('release_manual', `取消「${name}」手动控制`, '失败')
  }
}

// 批量取消手动控制（基于勾选）
async function onBatchReleaseManual() {
  const manualIds = selection.value.filter((r) => r.manualControl && r.status !== 2).map((r) => r.id)
  const faultCount = selection.value.filter((r) => r.status === 2).length
  const notManualCount = selection.value.filter((r) => !r.manualControl && r.status !== 2).length
  if (!manualIds.length) {
    let msg = ''
    if (faultCount > 0 && notManualCount > 0) {
      msg = `选中的 ${selection.value.length} 盏路灯中，${faultCount} 盏故障，${notManualCount} 盏已为自动控制，无可取消手动控制的路灯`
    } else if (faultCount > 0) {
      msg = `选中的 ${selection.value.length} 盏路灯均为故障状态，无法取消手动控制`
    } else {
      msg = `选中的 ${selection.value.length} 盏路灯均为自动控制，无需取消手动控制`
    }
    ElMessage.warning(msg)
    return
  }
  const confirmText = faultCount > 0 || notManualCount > 0
    ? `确定要取消选中的 ${manualIds.length} 盏路灯的手动控制吗？（${faultCount} 盏故障路灯和 ${notManualCount} 盏自动控制路灯将被跳过）`
    : `确定要取消选中的 ${manualIds.length} 盏路灯的手动控制吗？`
  try {
    await ElMessageBox.confirm(confirmText, '批量操作', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await releaseManualControlBatch(manualIds)
    const message = faultCount > 0 || notManualCount > 0
      ? `已取消 ${manualIds.length} 盏路灯的手动控制（${faultCount} 盏故障和 ${notManualCount} 盏自动控制路灯已跳过）`
      : `已取消 ${manualIds.length} 盏路灯的手动控制`
    ElMessage.success(message)
    await logOperation('batch_release_manual', `批量取消 ${manualIds.length} 盏路灯手动控制`, '成功')
    loadData()
    broadcastLightUpdate()
  } catch (e) {
    await logOperation('batch_release_manual', `批量取消 ${manualIds.length} 盏路灯手动控制`, '失败')
  }
}

// 分组取消手动控制
async function onGroupReleaseManual(group) {
  const groupData = filteredData.value.filter((d) => d[groupBy.value] === group.key)
  const manualIds = groupData.filter((d) => d.manualControl && d.status !== 2).map((d) => d.id)
  const faultCount = groupData.filter((d) => d.status === 2).length
  const notManualCount = groupData.filter((d) => !d.manualControl && d.status !== 2).length
  if (!manualIds.length) {
    let msg = ''
    if (faultCount > 0 && notManualCount > 0) {
      msg = `「${group.label}」分组下的 ${group.count} 盏路灯中，${faultCount} 盏故障，${notManualCount} 盏已为自动控制，无可取消手动控制的路灯`
    } else if (faultCount > 0) {
      msg = `「${group.label}」分组下的 ${group.count} 盏路灯均为故障状态，无法取消手动控制`
    } else {
      msg = `「${group.label}」分组下的 ${group.count} 盏路灯均为自动控制，无需取消手动控制`
    }
    ElMessage.warning(msg)
    return
  }
  const confirmText = faultCount > 0 || notManualCount > 0
    ? `确定要取消「${group.label}」分组下的 ${manualIds.length} 盏路灯的手动控制吗？（${faultCount} 盏故障路灯和 ${notManualCount} 盏自动控制路灯将被跳过）`
    : `确定要取消「${group.label}」分组下的 ${manualIds.length} 盏路灯的手动控制吗？`
  try {
    await ElMessageBox.confirm(confirmText, '分组操作', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await releaseManualControlBatch(manualIds)
    const message = faultCount > 0 || notManualCount > 0
      ? `已取消「${group.label}」 ${manualIds.length} 盏路灯的手动控制（${faultCount} 盏故障和 ${notManualCount} 盏自动控制路灯已跳过）`
      : `已取消「${group.label}」 ${manualIds.length} 盏路灯的手动控制`
    ElMessage.success(message)
    await logOperation('batch_release_manual', `分组取消「${group.label}」${manualIds.length} 盏路灯手动控制`, '成功')
    loadData()
    broadcastLightUpdate()
  } catch (e) {
    await logOperation('batch_release_manual', `分组取消「${group.label}」${manualIds.length} 盏路灯手动控制`, '失败')
  }
}

// 下发亮度
async function onApplyDimming() {
  if (!currentDimLight.value) return
  const row = currentDimLight.value
  const name = row.lightName || row.lightCode
  dimming.value = true
  try {
    await setLightBrightness(row.id, dimBrightness.value)
    ElMessage.success(`已设置「${name}」亮度为 ${dimBrightness.value}%`)
    await logOperation('dimming', `设置「${name}」亮度为 ${dimBrightness.value}%`, '成功')
    dimDialogVisible.value = false
    loadData()
    broadcastLightUpdate()
  } catch (e) {
    await logOperation('dimming', `设置「${name}」亮度为 ${dimBrightness.value}%`, '失败')
  } finally {
    dimming.value = false
  }
}

watch(() => appStore.lightDataVersion, () => {
  loadData()
})

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
.panel-card.full-width {
  flex: 1 1 100%;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dim-dialog-content {
  padding: 8px 0;
}
.dim-light-info {
  text-align: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}
.dim-light-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.dim-light-code {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.dim-slider-row {
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
.brightness-cell {
  display: flex;
  align-items: center;
}
</style>
