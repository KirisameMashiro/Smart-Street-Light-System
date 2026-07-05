<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">操作审计</h2>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-input
        v-model="query.operator"
        placeholder="操作人"
        clearable
        style="width: 180px"
        @keyup.enter="onSearch"
      />
      <el-date-picker
        v-model="timeRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 260px"
      />
      <el-select
        v-model="query.type"
        placeholder="操作类型"
        clearable
        style="width: 170px"
        @change="onSearch"
      >
        <el-option
          v-for="(label, key) in OPERATION_TYPE_MAP"
          :key="key"
          :label="label"
          :value="key"
        />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="操作时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="130" />
        <el-table-column prop="operatorName" label="操作人姓名" width="130" />
        <el-table-column label="类型" width="140">
          <template #default="{ row }">
            {{ OPERATION_TYPE_MAP[row.type] || row.type || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="content"
          label="操作内容"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.result === '成功' ? 'success' : 'danger'" size="small">
              {{ row.result || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IP" width="140">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
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
  </div>
</template>

<script setup>
defineOptions({ name: 'OperationAudit' })
import { ref, reactive, onMounted } from 'vue'
import { Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { getOperationLogPage } from '@/api/operation-log'
import { OPERATION_TYPE_MAP } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const timeRange = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  operator: undefined,
  type: undefined,
  startTime: undefined,
  endTime: undefined
})

async function loadData() {
  loading.value = true
  try {
    const params = { ...query }
    if (timeRange.value && timeRange.value.length === 2) {
      params.startTime = timeRange.value[0]
      params.endTime = timeRange.value[1]
    } else {
      params.startTime = undefined
      params.endTime = undefined
    }
    const res = await getOperationLogPage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    // 后端接口缺失或异常：错误已由拦截器提示，这里清空并复位
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.operator = undefined
  query.type = undefined
  timeRange.value = []
  onSearch()
}

onMounted(loadData)
</script>
