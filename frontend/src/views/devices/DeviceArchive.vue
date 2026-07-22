<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">设备档案</h2>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增路灯</el-button>
        <el-button
          type="success"
          :icon="Switch"
          :disabled="selection.length === 0"
          @click="onBatchSwitch(1)"
        >批量开灯</el-button>
        <el-button
          :icon="SwitchButton"
          :disabled="selection.length === 0"
          @click="onBatchSwitch(0)"
        >批量关灯</el-button>
        <el-button
          type="danger"
          :icon="Delete"
          :disabled="selection.length === 0"
          @click="onBatchDelete"
        >批量删除</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="编号 / 名称 / 位置"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select
        v-model="query.district"
        placeholder="动物园区"
        clearable
        style="width: 140px"
        @change="onDistrictChange"
      >
        <el-option
          v-for="o in districtOptions"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <el-select
        v-model="query.deviceType"
        placeholder="设备类型"
        clearable
        style="width: 160px"
        @change="onSearch"
      >
        <el-option
          v-for="o in deviceTypeOptions"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <el-select
        v-model="query.status"
        placeholder="状态"
        clearable
        style="width: 120px"
        @change="onSearch"
      >
        <el-option
          v-for="(item, key) in LIGHT_STATUS_MAP"
          :key="key"
          :label="item.label"
          :value="Number(key)"
        />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table
        :data="tableData"
        stripe
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="lightCode" label="编号" width="110" />
        <el-table-column prop="lightName" label="名称" width="130" show-overflow-tooltip />
        <el-table-column prop="location" label="安装位置" min-width="150" show-overflow-tooltip />
        <el-table-column label="动物园区" width="100">
          <template #default="{ row }">{{ row.district || '-' }}</template>
        </el-table-column>
        <el-table-column label="路段" width="100">
          <template #default="{ row }">{{ row.road || '-' }}</template>
        </el-table-column>
        <el-table-column label="设备类型" width="120">
          <template #default="{ row }">{{ row.deviceType || '-' }}</template>
        </el-table-column>
        <el-table-column label="投用日期" width="120">
          <template #default="{ row }">{{ formatDate(row.commissionDate) }}</template>
        </el-table-column>
        <el-table-column label="监控" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasCamera" type="success" size="small">有</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="广播" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasSpeaker" type="success" size="small">有</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="额定功率(W)" width="110">
          <template #default="{ row }">{{ row.ratedPower ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="LIGHT_STATUS_MAP[row.status]?.type" size="small">
              {{ LIGHT_STATUS_MAP[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="brightness" label="亮度(%)" width="90" />
        <el-table-column label="操作" width="200" fixed="right" class-name="table-ops">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push('/devices/' + row.id)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑路灯' : '新增路灯'"
      width="640px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="路灯编号" prop="lightCode">
              <el-input v-model="form.lightCode" placeholder="如 SL-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路灯名称" prop="lightName">
              <el-input v-model="form.lightName" placeholder="如 路灯A-01" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="安装位置" prop="location">
              <el-input v-model="form.location" placeholder="安装位置" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="动物园区" prop="district">
              <el-select v-model="form.district" placeholder="请选择" clearable style="width:100%" @change="onFormDistrictChange">
                <el-option
                  v-for="o in districtOptions"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路段" prop="road">
              <el-select v-model="form.road" placeholder="请选择" clearable style="width:100%">
                <el-option
                  v-for="o in formFilteredRoadOptions"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经度" prop="longitude">
              <el-input-number
                v-model="form.longitude"
                :precision="6"
                :controls="false"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="latitude">
              <el-input-number
                v-model="form.latitude"
                :precision="6"
                :controls="false"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select
                v-model="form.status"
                style="width:100%"
                :disabled="originalStatus === 2"
              >
                <el-option
                  v-for="(item, key) in LIGHT_STATUS_MAP"
                  :key="key"
                  :label="item.label"
                  :value="Number(key)"
                />
              </el-select>
              <el-alert
                v-if="originalStatus === 2"
                type="warning"
                :closable="false"
                show-icon
                style="margin-top: 4px"
              >
                <template #title>该路灯处于故障状态，不可编辑状态。请前往「故障处理」页面进行修复。</template>
              </el-alert>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="亮度(%)" prop="brightness">
              <el-input-number v-model="form.brightness" :min="0" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型" prop="deviceType">
              <el-select
                v-model="form.deviceType"
                placeholder="请选择"
                clearable
                filterable
                style="width:100%"
                @change="onFormDeviceTypeChange"
              >
                <el-option
                  v-for="o in deviceTypeOptions"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="额定功率(W)" prop="ratedPower">
              <el-input-number v-model="form.ratedPower" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投用日期" prop="commissionDate">
              <el-date-picker
                v-model="form.commissionDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'DeviceArchive' })
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Search,
  RefreshLeft,
  Switch,
  SwitchButton,
  Delete
} from '@element-plus/icons-vue'
import {
  getLightPage,
  addLight,
  updateLight,
  deleteLight,
  batchSwitchLight,
  getAllLights
} from '@/api/light'
import { getSystemDistricts, getSystemRoads, getSystemDeviceTypes } from '@/api/system'
import { LIGHT_STATUS_MAP } from '@/utils/constants'
import { formatDate } from '@/utils/format'
import { logOperation } from '@/utils/log'
import { useAppStore } from '@/store/app'

const appStore = useAppStore()

const syncChannel = typeof BroadcastChannel !== 'undefined'
  ? new BroadcastChannel('smartlight_light_detail')
  : null

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const selection = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  district: undefined,
  deviceType: undefined,
  status: undefined
})

const districtOptions = ref([])
const deviceTypeOptions = ref([])
const deviceTypeMap = ref({})

async function loadOptions() {
  try {
    const [dRes, tRes] = await Promise.all([
      getSystemDistricts(),
      getSystemDeviceTypes()
    ])
    districtOptions.value = (dRes.data || []).map((d) => ({ value: d.districtName, label: d.districtName }))
    const deviceTypes = tRes.data || []
    deviceTypeOptions.value = deviceTypes.map((t) => ({ value: t.typeName, label: t.typeName }))
    deviceTypeMap.value = {}
    deviceTypes.forEach((t) => {
      if (t.typeName) {
        deviceTypeMap.value[t.typeName] = { hasCamera: !!t.hasCamera, hasSpeaker: !!t.hasSpeaker, ratedPower: t.ratedPower }
      }
    })
  } catch (e) {}
}

async function loadData() {
  loading.value = true
  try {
    const res = await getLightPage(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    // 错误提示由拦截器统一处理，这里仅清空数据/loading 复位
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onFormDeviceTypeChange(val) {
  const info = deviceTypeMap.value[val]
  if (info) {
    form.hasCamera = info.hasCamera
    form.hasSpeaker = info.hasSpeaker
    form.ratedPower = info.ratedPower
  }
}

function onDistrictChange() {
  query.pageNum = 1
  loadData()
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.district = undefined
  query.deviceType = undefined
  query.status = undefined
  onSearch()
}

function onSelectionChange(rows) {
  selection.value = rows
}

// 批量开关灯
async function onBatchSwitch(status) {
  const ids = selection.value.map((r) => r.id)
  const text = status === 1 ? '开启' : '关闭'
  try {
    await ElMessageBox.confirm(
      `确定要${text}选中的 ${ids.length} 盏路灯吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await batchSwitchLight(ids, status)
    ElMessage.success(`已${text} ${ids.length} 盏路灯`)
    logOperation(
      'batch_switch',
      `批量${text} ${ids.length} 盏路灯（${ids.join(',')}）`,
      '成功'
    )
    appStore.notifyLightDataChanged()
    loadData()
  } catch (e) {
    // 失败提示由拦截器处理
  }
}

// 批量删除：循环调用 deleteLight
async function onBatchDelete() {
  const rows = [...selection.value]
  const ids = rows.map((r) => r.id)
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${ids.length} 盏路灯吗？删除后不可恢复。`,
      '批量删除确认',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  loading.value = true
  let ok = 0
  let fail = 0
  for (const id of ids) {
    try {
      await deleteLight(id)
      ok++
    } catch (e) {
      fail++
    }
  }
  loading.value = false
  if (ok > 0) {
    ElMessage.success(`成功删除 ${ok} 盏路灯${fail ? `，失败 ${fail} 盏` : ''}`)
    logOperation(
      'user_delete',
      `批量删除路灯 ${ok} 盏`,
      fail ? `部分失败(${fail})` : '成功'
    )
    appStore.notifyLightDataChanged()
  }
  loadData()
}

// 单条删除
async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除路灯「${row.lightName || row.lightCode}」吗？`,
      '删除确认',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteLight(row.id)
    ElMessage.success('删除成功')
    logOperation('user_delete', `删除路灯 ${row.lightCode || row.lightName}`, '成功')
    appStore.notifyLightDataChanged()
    loadData()
  } catch (e) {
    // 失败提示由拦截器处理
  }
}

// 弹窗
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const originalStatus = ref(0)
const form = reactive({
  id: undefined,
  lightCode: '',
  lightName: '',
  location: '',
  district: undefined,
  road: undefined,
  longitude: undefined,
  latitude: undefined,
  status: 0,
  brightness: 0,
  deviceType: undefined,
  ratedPower: undefined,
  hasCamera: false,
  hasSpeaker: false,
  commissionDate: undefined,
  remark: ''
})

const rules = {
  lightCode: [{ required: true, message: '请输入路灯编号', trigger: 'blur' }],
  lightName: [{ required: true, message: '请输入路灯名称', trigger: 'blur' }]
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, row)
    originalStatus.value = row.status ?? 0
  } else {
    resetForm()
    originalStatus.value = 0
  }
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    lightCode: '',
    lightName: '',
    location: '',
    district: undefined,
    road: undefined,
    longitude: undefined,
    latitude: undefined,
    status: 0,
    brightness: 0,
    deviceType: undefined,
    ratedPower: undefined,
    hasCamera: false,
    hasSpeaker: false,
    commissionDate: undefined,
    remark: ''
  })
  formRef.value?.clearValidate()
}

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateLight({ ...form })
      ElMessage.success('更新成功')
      logOperation(
        'user_update',
        `修改路灯 ${form.lightCode || form.lightName}`,
        '成功'
      )
    } else {
      const { id, ...payload } = form
      await addLight(payload)
      ElMessage.success('新增成功')
      logOperation(
        'user_create',
        `新增路灯 ${form.lightCode || form.lightName}`,
        '成功'
      )
    }
    dialogVisible.value = false
    appStore.notifyLightDataChanged()
    loadData()
  } catch (e) {
    // 失败提示由拦截器处理
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadOptions()
  loadData()
  if (syncChannel) {
    syncChannel.addEventListener('message', (e) => {
      const msg = e.data
      if (msg && msg.type === 'light:updated') {
        loadData()
      }
    })
  }
})

onUnmounted(() => {
  if (syncChannel) {
    syncChannel.close()
  }
})
</script>
