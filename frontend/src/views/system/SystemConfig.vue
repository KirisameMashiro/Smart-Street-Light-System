<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">参数配置</h2>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- 系统参数 -->
      <el-tab-pane label="系统参数" name="system">
        <div class="table-card" v-loading="configLoading">
          <el-form
            ref="configFormRef"
            :model="configForm"
            label-width="160px"
            style="max-width: 640px; padding: 20px 16px"
          >
            <el-form-item label="系统名称">
              <el-input v-model="configForm.systemName" placeholder="智慧路灯管理系统" />
            </el-form-item>
            <el-form-item label="区域碳排放因子">
              <el-input-number
                v-model="configForm.carbonFactor"
                :min="0"
                :step="0.1"
                :precision="3"
                controls-position="right"
              />
              <span class="form-unit">kgCO2/kWh</span>
            </el-form-item>
            <el-form-item label="默认调光亮度">
              <el-input-number
                v-model="configForm.defaultBrightness"
                :min="0"
                :max="100"
                :step="5"
                controls-position="right"
              />
              <span class="form-unit">%</span>
            </el-form-item>
            <el-form-item label="数据采集间隔">
              <el-input-number
                v-model="configForm.collectInterval"
                :min="1"
                :step="10"
                controls-position="right"
              />
              <span class="form-unit">秒</span>
            </el-form-item>
            <el-form-item label="离线判定阈值">
              <el-input-number
                v-model="configForm.offlineThreshold"
                :min="1"
                :step="1"
                controls-position="right"
              />
              <span class="form-unit">分钟</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="configSaving" @click="onSaveConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 告警规则 -->
      <el-tab-pane label="告警规则" name="rule">
        <div class="table-card">
          <div class="toolbar" style="padding: 12px 16px">
            <el-button type="primary" :icon="Plus" @click="openRuleDialog()">新增规则</el-button>
            <el-button :icon="Refresh" :loading="ruleLoading" @click="loadRules">刷新</el-button>
          </div>
          <el-table :data="rules" v-loading="ruleLoading" stripe>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column label="规则类型" width="140">
              <template #default="{ row }">
                {{ ALERT_RULE_TYPE_MAP[row.ruleType] || row.ruleType || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="threshold" label="阈值" width="120" />
            <el-table-column label="报警级别" width="120">
              <template #default="{ row }">
                <el-tag :type="ALERT_LEVEL_MAP[row.level]?.type" size="small">
                  {{ ALERT_LEVEL_MAP[row.level]?.label || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="启用" width="100">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="onToggleEnabled(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right" class-name="table-ops">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRuleDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="onRuleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑规则弹窗 -->
    <el-dialog
      v-model="ruleDialogVisible"
      :title="ruleIsEdit ? '编辑告警规则' : '新增告警规则'"
      width="520px"
      @closed="resetRuleForm"
    >
      <el-form
        ref="ruleFormRef"
        :model="ruleForm"
        :rules="ruleRules"
        label-width="100px"
      >
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="ruleForm.ruleType" placeholder="请选择规则类型" style="width: 100%">
            <el-option
              v-for="(label, key) in ALERT_RULE_TYPE_MAP"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值" prop="threshold">
          <el-input-number
            v-model="ruleForm.threshold"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报警级别" prop="level">
          <el-select v-model="ruleForm.level" placeholder="请选择报警级别" style="width: 100%">
            <el-option
              v-for="opt in levelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="ruleForm.description"
            type="textarea"
            :rows="3"
            placeholder="规则描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitting" @click="onRuleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  getSystemConfig,
  updateSystemConfig,
  getAlertRules,
  addAlertRule,
  updateAlertRule,
  deleteAlertRule
} from '@/api/config'
import { logOperation } from '@/utils/log'
import { ALERT_RULE_TYPE_MAP, ALERT_LEVEL_MAP } from '@/utils/constants'

const activeTab = ref('system')

// ============ 系统参数 ============
const configLoading = ref(false)
const configSaving = ref(false)
const configFormRef = ref()
const configForm = reactive({
  systemName: '',
  carbonFactor: 0.5,
  defaultBrightness: 80,
  collectInterval: 60,
  offlineThreshold: 5
})

async function loadConfig() {
  configLoading.value = true
  try {
    const res = await getSystemConfig()
    if (res.data) {
      Object.assign(configForm, res.data)
    }
  } catch (e) {
    // 后端接口缺失：保留默认值，错误已由拦截器提示
  } finally {
    configLoading.value = false
  }
}

async function onSaveConfig() {
  configSaving.value = true
  try {
    await updateSystemConfig({ ...configForm })
    ElMessage.success('保存成功')
    logOperation('config_update', '修改系统参数配置')
  } catch (e) {
    // 拦截器已提示
  } finally {
    configSaving.value = false
  }
}

// ============ 告警规则 ============
const ruleLoading = ref(false)
const ruleSubmitting = ref(false)
const rules = ref([])

const levelOptions = computed(() =>
  Object.entries(ALERT_LEVEL_MAP)
    .filter(([k]) => Number(k) >= 2)
    .map(([k, v]) => ({ value: Number(k), label: v.label }))
)

async function loadRules() {
  ruleLoading.value = true
  try {
    const res = await getAlertRules()
    rules.value = res.data || []
  } catch (e) {
    rules.value = []
  } finally {
    ruleLoading.value = false
  }
}

// 新增/编辑弹窗
const ruleDialogVisible = ref(false)
const ruleIsEdit = ref(false)
const ruleFormRef = ref()
const ruleForm = reactive({
  id: undefined,
  ruleType: undefined,
  threshold: 0,
  level: 2,
  enabled: true,
  description: ''
})

const ruleRules = {
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值', trigger: 'blur' }],
  level: [{ required: true, message: '请选择报警级别', trigger: 'change' }]
}

function resetRuleForm() {
  Object.assign(ruleForm, {
    id: undefined,
    ruleType: undefined,
    threshold: 0,
    level: 2,
    enabled: true,
    description: ''
  })
  ruleFormRef.value?.clearValidate()
}

function openRuleDialog(row) {
  ruleIsEdit.value = !!row
  if (row) {
    Object.assign(ruleForm, row)
  } else {
    resetRuleForm()
  }
  ruleDialogVisible.value = true
}

async function onRuleSubmit() {
  try {
    await ruleFormRef.value.validate()
  } catch (e) {
    return
  }
  ruleSubmitting.value = true
  try {
    const ruleLabel = ALERT_RULE_TYPE_MAP[ruleForm.ruleType] || ruleForm.ruleType
    if (ruleIsEdit.value) {
      await updateAlertRule({ ...ruleForm })
      ElMessage.success('更新成功')
      logOperation('config_update', `修改告警规则：${ruleLabel}`)
    } else {
      const { id, ...payload } = ruleForm
      await addAlertRule(payload)
      ElMessage.success('新增成功')
      logOperation('config_update', `新增告警规则：${ruleLabel}`)
    }
    ruleDialogVisible.value = false
    loadRules()
  } catch (e) {
    // 拦截器已提示
  } finally {
    ruleSubmitting.value = false
  }
}

async function onRuleDelete(row) {
  const ruleLabel = ALERT_RULE_TYPE_MAP[row.ruleType] || row.ruleType
  try {
    await ElMessageBox.confirm(`确定删除规则「${ruleLabel}」吗？`, '删除确认', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteAlertRule(row.id)
    ElMessage.success('删除成功')
    logOperation('config_update', `删除告警规则：${ruleLabel}`)
    loadRules()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onToggleEnabled(row) {
  try {
    await updateAlertRule({ ...row })
    ElMessage.success(row.enabled ? '已启用' : '已停用')
    logOperation('config_update', `${row.enabled ? '启用' : '停用'}告警规则：${ALERT_RULE_TYPE_MAP[row.ruleType] || row.ruleType}`)
  } catch (e) {
    // 失败回滚开关状态
    row.enabled = !row.enabled
  }
}

onMounted(() => {
  loadConfig()
  loadRules()
})
</script>

<style scoped>
.config-tabs {
  background: #fff;
  border-radius: 6px;
  box-shadow: var(--card-shadow);
  padding: 0 16px 16px;
}

.form-unit {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
}
</style>
