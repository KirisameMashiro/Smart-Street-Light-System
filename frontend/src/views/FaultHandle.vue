<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">
        故障处理
        <el-tag v-if="faultCount > 0" type="danger" size="small" style="margin-left:8px">
          {{ faultCount }} 盏故障
        </el-tag>
        <el-tag v-else type="success" size="small" style="margin-left:8px">
          无故障
        </el-tag>
      </h2>
      <div class="toolbar">
        <span class="text-muted">实时刷新</span>
        <el-switch v-model="autoRefresh" />
        <el-input-number
          v-model="refreshInterval"
          :min="1"
          :max="10"
          :step="1"
          :disabled="!autoRefresh"
          size="small"
          style="width: 100px"
        />
        <span class="text-muted">秒</span>
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索编号/名称/位置"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select
        v-model="filterDistrict"
        placeholder="行政区"
        clearable
        style="width: 160px"
        @change="onSearch"
      >
        <el-option v-for="d in districtOptions" :key="d" :label="d" :value="d" />
      </el-select>
      <el-select
        v-model="filterRoad"
        placeholder="路段"
        clearable
        style="width: 160px"
        @change="onSearch"
      >
        <el-option v-for="r in filteredRoads" :key="r" :label="r" :value="r" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <div class="table-wrapper">
        <el-table :data="tableData" stripe v-loading="loading">
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="lightCode" label="编号" width="130" />
          <el-table-column prop="lightName" label="名称" width="150" show-overflow-tooltip />
          <el-table-column prop="location" label="安装位置" min-width="200" show-overflow-tooltip />
          <el-table-column prop="district" label="行政区" width="110" />
          <el-table-column prop="road" label="路段" width="110" />
          <el-table-column prop="deviceType" label="设备类型" width="100" />
          <el-table-column label="当前亮度" width="90">
            <template #default="{ row }">{{ row.brightness ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag type="danger" size="small">故障</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
              <el-button link type="success" :icon="Check" @click="openFix(row)">修复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-if="tableData.length === 0 && !loading" class="empty-tip">
        <el-empty description="暂无故障路灯" />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="故障路灯详情" width="520px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="编号">{{ current.lightCode }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ current.lightName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="行政区">{{ current.district || '-' }}</el-descriptions-item>
        <el-descriptions-item label="路段">{{ current.road || '-' }}</el-descriptions-item>
        <el-descriptions-item label="安装位置" :span="2">{{ current.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备类型">{{ current.deviceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前亮度">{{ current.brightness ?? '-' }}%</el-descriptions-item>
        <el-descriptions-item label="经度">{{ current.longitude ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="纬度">{{ current.latitude ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag type="danger" size="small">故障</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 修复弹窗 -->
    <el-dialog v-model="fixVisible" title="修复故障路灯" width="520px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          确认修复后，路灯状态将恢复为「关闭」，并自动记录工作日志。
        </template>
      </el-alert>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="编号">{{ current.lightCode }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ current.lightName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="行政区">{{ current.district || '-' }}</el-descriptions-item>
        <el-descriptions-item label="路段">{{ current.road || '-' }}</el-descriptions-item>
        <el-descriptions-item label="安装位置">{{ current.location || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="fixForm" label-width="90px">
        <el-form-item label="处理人" required>
          <el-input v-model="fixForm.handler" placeholder="请输入处理人姓名" />
        </el-form-item>
        <el-form-item label="故障原因">
          <el-input v-model="fixForm.faultReason" type="textarea" :rows="2" placeholder="可选：描述故障原因" />
        </el-form-item>
        <el-form-item label="处理措施">
          <el-input v-model="fixForm.fixMethod" type="textarea" :rows="2" placeholder="可选：描述处理措施" />
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="fixForm.remark" type="textarea" :rows="2" placeholder="可选：其他备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fixVisible = false">取消</el-button>
        <el-button type="success" :loading="submitting" @click="onSubmitFix">确认修复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'FaultHandle' })
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Refresh, View, Check } from '@element-plus/icons-vue'
import { getAllLights, getLightPage, updateLight } from '@/api/light'
import { addOperationLog } from '@/api/operation-log'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'

const userStore = useUserStore()
const appStore = useAppStore()
const loading = ref(false)
const submitting = ref(false)
const autoRefresh = ref(true)
const refreshInterval = ref(10)
const allLights = ref([])
const tableData = ref([])
const searchKeyword = ref('')
const filterDistrict = ref('')
const filterRoad = ref('')
let pollTimer = null

const detailVisible = ref(false)
const fixVisible = ref(false)
const current = ref({})
const fixForm = reactive({
  handler: '',
  faultReason: '',
  fixMethod: '',
  remark: ''
})

// 行政区与路段映射
const districtRoadMap = {}

const faultCount = computed(() => allLights.value.filter(l => Number(l.status) === 2).length)

const districtOptions = computed(() => {
  const set = new Set(allLights.value.filter(l => Number(l.status) === 2).map(l => l.district).filter(Boolean))
  return Array.from(set)
})

const roadOptions = computed(() => {
  const set = new Set(allLights.value.filter(l => Number(l.status) === 2).map(l => l.road).filter(Boolean))
  return Array.from(set)
})

const filteredRoads = computed(() => {
  if (!filterDistrict.value) return roadOptions.value
  return districtRoadMap[filterDistrict.value] || []
})

async function loadData() {
  loading.value = true
  try {
    const res = await getLightPage({ pageNum: 1, pageSize: 9999 })
    allLights.value = res.data?.records || []
    buildDistrictRoadMap()
    filterData()
  } finally {
    loading.value = false
  }
}

function buildDistrictRoadMap() {
  allLights.value.forEach(light => {
    if (light.district && light.road) {
      if (!districtRoadMap[light.district]) {
        districtRoadMap[light.district] = []
      }
      if (!districtRoadMap[light.district].includes(light.road)) {
        districtRoadMap[light.district].push(light.road)
      }
    }
  })
}

function filterData() {
  let list = allLights.value.filter(l => Number(l.status) === 2)

  if (searchKeyword.value) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(l =>
      (l.lightCode && l.lightCode.toLowerCase().includes(kw)) ||
      (l.lightName && l.lightName.toLowerCase().includes(kw)) ||
      (l.location && l.location.toLowerCase().includes(kw))
    )
  }

  if (filterDistrict.value) {
    list = list.filter(l => l.district === filterDistrict.value)
  }

  if (filterRoad.value) {
    list = list.filter(l => l.road === filterRoad.value)
  }

  tableData.value = list
}

function onSearch() {
  filterData()
}

function onReset() {
  searchKeyword.value = ''
  filterDistrict.value = ''
  filterRoad.value = ''
  filterData()
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

function openFix(row) {
  current.value = row
  fixForm.handler = userStore.user?.realName || userStore.user?.username || ''
  fixForm.faultReason = ''
  fixForm.fixMethod = ''
  fixForm.remark = ''
  fixVisible.value = true
}

async function onSubmitFix() {
  if (!fixForm.handler) {
    ElMessage.warning('请输入处理人')
    return
  }
  submitting.value = true
  try {
    // 1. 更新路灯状态为关闭
    await updateLight({
      id: current.value.id,
      status: 0
    })

    // 2. 记录操作日志
    try {
      await addOperationLog({
        type: 'fault_repair',
        operator: fixForm.handler,
        operatorName: fixForm.handler,
        content: `修复故障路灯 ${current.value.lightCode}（${current.value.lightName || ''}）` +
          `${fixForm.faultReason ? '，故障原因：' + fixForm.faultReason : ''}` +
          `${fixForm.fixMethod ? '，处理措施：' + fixForm.fixMethod : ''}` +
          `${fixForm.remark ? '，备注：' + fixForm.remark : ''}`,
        result: '成功'
      })
    } catch (e) {
      console.warn('记录操作日志失败', e)
    }

    ElMessage.success('路灯修复成功，已恢复关闭状态')
    fixVisible.value = false
    appStore.notifyLightDataChanged()
    loadData()
  } catch (e) {
    ElMessage.error('修复失败：' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

// 实时刷新
function startPolling() {
  stopPolling()
  if (!autoRefresh.value) return
  pollTimer = setInterval(() => {
    loadData()
  }, refreshInterval.value * 1000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

import { watch } from 'vue'
watch(autoRefresh, (v) => {
  if (v) startPolling()
  else stopPolling()
})
watch(refreshInterval, () => {
  if (autoRefresh.value) startPolling()
})

onMounted(() => {
  loadData()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.empty-tip {
  padding: 60px 0;
}

@media (max-width: 768px) {
  .table-wrapper {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    -ms-overflow-style: -ms-autohiding-scrollbar;
  }

  .table-wrapper::-webkit-scrollbar {
    height: 6px;
  }

  .table-wrapper::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 3px;
  }

  .table-wrapper::-webkit-scrollbar-thumb {
    background: #c1c1c1;
    border-radius: 3px;
  }
}
</style>
