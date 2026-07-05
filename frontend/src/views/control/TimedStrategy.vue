<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">定时策略</h2>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增策略</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="STRATEGY_TYPE_MAP[row.type]?.type" size="small">
              {{ STRATEGY_TYPE_MAP[row.type]?.label || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="适用星期" min-width="180">
          <template #default="{ row }">
            <template v-if="row.weekdays && row.weekdays.length">
              <el-tag
                v-for="w in row.weekdays"
                :key="w"
                size="small"
                style="margin-right: 4px"
              >{{ weekdayLabel(w) }}</el-tag>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="时段" width="160">
          <template #default="{ row }">{{ row.startTime || '--' }} ~ {{ row.endTime || '--' }}</template>
        </el-table-column>
        <el-table-column label="目标亮度" width="100">
          <template #default="{ row }">{{ row.brightness ?? 0 }}%</template>
        </el-table-column>
        <el-table-column prop="targetGroup" label="适用分组" width="110">
          <template #default="{ row }">{{ row.targetGroup || '-' }}</template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="!!row.enabled"
              @change="(val) => onToggleEnabled(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" class-name="table-ops">
          <template #default="{ row }">
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
      :title="isEdit ? '编辑策略' : '新增策略'"
      width="620px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="策略名称" prop="name">
          <el-input v-model="form.name" placeholder="如 工作日傍晚开灯" />
        </el-form-item>
        <el-form-item label="策略类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option
              v-for="(item, key) in STRATEGY_TYPE_MAP"
              :key="key"
              :label="item.label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="适用星期" prop="weekdays">
          <el-checkbox-group v-model="form.weekdays">
            <el-checkbox
              v-for="w in WEEKDAY_OPTIONS"
              :key="w.value"
              :value="w.value"
            >{{ w.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-picker
            v-model="form.startTime"
            value-format="HH:mm:ss"
            format="HH:mm:ss"
            placeholder="开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-picker
            v-model="form.endTime"
            value-format="HH:mm:ss"
            format="HH:mm:ss"
            placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="目标亮度" prop="brightness">
          <el-slider v-model="form.brightness" :min="0" :max="100" show-input style="width: 100%" />
        </el-form-item>
        <el-form-item label="适用分组" prop="targetGroup">
          <el-select v-model="form.targetGroup" clearable placeholder="选择行政区/路段" style="width: 100%">
            <el-option-group label="行政区">
              <el-option v-for="o in DISTRICT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-option-group>
            <el-option-group label="路段">
              <el-option v-for="o in ROAD_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'TimedStrategy' })
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  getStrategyPage,
  addStrategy,
  updateStrategy,
  deleteStrategy,
  toggleStrategy
} from '@/api/control'
import {
  STRATEGY_TYPE_MAP,
  WEEKDAY_OPTIONS,
  DISTRICT_OPTIONS,
  ROAD_OPTIONS
} from '@/utils/constants'
import { logOperation } from '@/utils/log'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10
})

function weekdayLabel(val) {
  return WEEKDAY_OPTIONS.find((o) => o.value === val)?.label || val
}

async function loadData() {
  loading.value = true
  try {
    const res = await getStrategyPage(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    // 后端接口缺失：清空列表，错误已由拦截器提示
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 启停
async function onToggleEnabled(row, val) {
  try {
    await toggleStrategy(row.id, val)
    row.enabled = val
    ElMessage.success(val ? '已启用' : '已停用')
    await logOperation('strategy_enable', `${val ? '启用' : '停用'}策略「${row.name}」`, '成功')
  } catch (e) {
    // 失败保持原状态
    await logOperation('strategy_enable', `${val ? '启用' : '停用'}策略「${row.name}」`, '失败')
  }
}

// 删除
async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除策略「${row.name}」吗？`, '删除确认', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteStrategy(row.id)
    ElMessage.success('删除成功')
    await logOperation('strategy_delete', `删除策略「${row.name}」`, '成功')
    loadData()
  } catch (e) {
    await logOperation('strategy_delete', `删除策略「${row.name}」`, '失败')
  }
}

// 弹窗
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: undefined,
  name: '',
  type: 'everyday',
  weekdays: [],
  startTime: '18:00:00',
  endTime: '06:00:00',
  brightness: 80,
  targetGroup: '',
  enabled: true
})

const rules = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择策略类型', trigger: 'change' }]
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      type: row.type || 'everyday',
      weekdays: Array.isArray(row.weekdays) ? [...row.weekdays] : [],
      startTime: row.startTime || '18:00:00',
      endTime: row.endTime || '06:00:00',
      brightness: row.brightness ?? 80,
      targetGroup: row.targetGroup || '',
      enabled: !!row.enabled
    })
  } else {
    resetForm()
  }
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    type: 'everyday',
    weekdays: [],
    startTime: '18:00:00',
    endTime: '06:00:00',
    brightness: 80,
    targetGroup: '',
    enabled: true
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
      await updateStrategy({ ...form })
      ElMessage.success('更新成功')
      await logOperation('strategy_update', `修改策略「${form.name}」`, '成功')
    } else {
      const { id, ...payload } = form
      await addStrategy(payload)
      ElMessage.success('新增成功')
      await logOperation('strategy_add', `新增策略「${form.name}」`, '成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    await logOperation(
      isEdit.value ? 'strategy_update' : 'strategy_add',
      `${isEdit.value ? '修改' : '新增'}策略「${form.name}」`,
      '失败'
    )
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>
