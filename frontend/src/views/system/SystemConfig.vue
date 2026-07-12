<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">参数配置</h2>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- 系统参数 -->
      <el-tab-pane label="系统参数" name="system">
        <div class="table-card">
          <el-form
            ref="configFormRef"
            :model="configForm"
            label-width="200px"
            style="max-width: 560px; padding: 20px 16px"
          >
            <el-form-item label="阈值联动判定周期">
              <el-input-number
                v-model="configForm.thresholdCheckInterval"
                :min="10"
                :max="600"
                :step="10"
                controls-position="right"
              />
              <span class="form-unit">秒</span>
              <span class="form-tip">每隔多少秒执行一次阈值联动判定</span>
            </el-form-item>

            <el-form-item label="失联判定故障时间">
              <el-input-number
                v-model="configForm.offlineFaultTimeout"
                :min="60"
                :max="3600"
                :step="30"
                controls-position="right"
              />
              <span class="form-unit">秒</span>
              <span class="form-tip">超过此时间未收到传感器数据视为失联故障</span>
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
          <el-table :data="rules" stripe>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="ruleName" label="规则名称" width="160" show-overflow-tooltip />
            <el-table-column label="规则类型" width="140">
              <template #default="{ row }">
                {{ ALERT_RULE_TYPE_MAP[row.ruleType]?.label || row.ruleType || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="threshold" label="阈值表达式" min-width="200" show-overflow-tooltip />
            <el-table-column label="报警级别" width="120">
              <template #default="{ row }">
                <el-tag :type="ALERT_LEVEL_MAP[ruleLevelOf(row.ruleType)]?.type" size="small">
                  {{ ALERT_LEVEL_MAP[ruleLevelOf(row.ruleType)]?.label || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
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
      width="640px"
      @closed="resetRuleForm"
    >
      <el-form
        ref="ruleFormRef"
        :model="ruleForm"
        :rules="ruleRules"
        label-width="100px"
      >
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="ruleForm.ruleType" placeholder="请选择规则类型" style="width: 100%" @change="onRuleTypeChange">
            <el-option
              v-for="(info, key) in ALERT_RULE_TYPE_MAP"
              :key="key"
              :label="info.label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleForm.ruleName" placeholder="如：路灯故障检测" />
        </el-form-item>

        <!-- 阈值表达式编辑器 -->
        <el-form-item label="阈值条件" prop="threshold">
          <div class="threshold-editor">
            <div
              v-for="(cond, idx) in conditions"
              :key="idx"
              class="condition-row"
            >
              <span v-if="idx > 0" class="or-label">或</span>
              <el-select
                v-model="cond.field"
                placeholder="字段"
                style="width: 110px"
                @change="onFieldChange(idx)"
              >
                <el-option
                  v-for="f in THRESHOLD_FIELD_OPTIONS"
                  :key="f.value"
                  :label="f.label"
                  :value="f.value"
                />
              </el-select>
              <el-select
                v-model="cond.operator"
                placeholder="运算符"
                style="width: 100px"
              >
                <el-option
                  v-for="op in THRESHOLD_OPERATOR_OPTIONS"
                  :key="op.value"
                  :label="op.value"
                  :value="op.value"
                />
              </el-select>
              <el-checkbox
                v-if="cond.field === '功率'"
                v-model="cond.useRated"
                style="margin-left: 4px"
              >额定值</el-checkbox>
              <template v-if="cond.useRated">
                <span style="color: #909399; font-size: 13px">额定值</span>
                <span style="color: #909399; font-size: 13px">*</span>
                <el-input-number
                  v-model="cond.multiplier"
                  :min="0.1"
                  :step="0.1"
                  :precision="1"
                  size="small"
                  style="width: 90px"
                />
              </template>
              <template v-else>
                <el-input-number
                  v-model="cond.value"
                  :step="1"
                  size="small"
                  style="width: 120px"
                />
              </template>
              <span class="cond-unit">{{ cond.unit }}</span>
              <el-button
                v-if="conditions.length > 1"
                link
                type="danger"
                :icon="Delete"
                @click="removeCondition(idx)"
              />
            </div>
            <el-button link type="primary" :icon="Plus" @click="addCondition">
              添加"或"条件
            </el-button>

            <div class="time-constraint-row">
              <span class="tc-label">时间约束：</span>
              <el-select v-model="timeConstraint" style="width: 220px">
                <el-option
                  v-for="opt in TIME_CONSTRAINT_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>

            <div class="threshold-preview">
              <span class="preview-label">表达式预览：</span>
              <code class="preview-code">{{ thresholdPreview || '（请填写条件）' }}</code>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="ruleForm.description"
            type="textarea"
            :rows="3"
            placeholder="规则描述"
          />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="ruleForm.enabled" />
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
defineOptions({ name: 'SystemConfig' })
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Delete } from '@element-plus/icons-vue'
import {
  getSystemConfig,
  updateSystemConfig,
  getAlertRules,
  addAlertRule,
  updateAlertRule,
  deleteAlertRule
} from '@/api/config'
import { logOperation } from '@/utils/log'
import {
  ALERT_RULE_TYPE_MAP,
  ALERT_LEVEL_MAP,
  THRESHOLD_FIELD_OPTIONS,
  THRESHOLD_OPERATOR_OPTIONS,
  TIME_CONSTRAINT_OPTIONS
} from '@/utils/constants'

const activeTab = ref('system')

// 规则类型 → 报警级别（后端自动映射）
function ruleLevelOf(ruleType) {
  return ALERT_RULE_TYPE_MAP[ruleType]?.level || 2
}

// ============ 系统参数 ============
const configLoading = ref(false)
const configSaving = ref(false)
const configFormRef = ref()
const configForm = reactive({
  // 两个 system_config key 的配置值
  thresholdCheckInterval: 60,
  offlineFaultTimeout: 300
})

/** 保存 system_config 中每条配置的 { id, configKey } 映射，用于更新时传参 */
const configKeyMeta = {}

async function loadConfig() {
  configLoading.value = true
  try {
    const res = await getSystemConfig()
    if (res.data && Array.isArray(res.data)) {
      // 将后端 key-value 列表映射到表单字段
      for (const item of res.data) {
        configKeyMeta[item.configKey] = { id: item.id, configKey: item.configKey }
        switch (item.configKey) {
          case 'threshold_check_interval':
            configForm.thresholdCheckInterval = parseInt(item.configValue) || 60
            break
          case 'offline_fault_timeout':
            configForm.offlineFaultTimeout = parseInt(item.configValue) || 300
            break
        }
      }
    }
  } catch (e) {
  } finally {
    configLoading.value = false
  }
}

async function onSaveConfig() {
  configSaving.value = true
  try {
    // 逐个 key 更新
    const updates = [
      { key: 'threshold_check_interval', value: String(configForm.thresholdCheckInterval) },
      { key: 'offline_fault_timeout', value: String(configForm.offlineFaultTimeout) }
    ]
    for (const update of updates) {
      const meta = configKeyMeta[update.key]
      if (meta) {
        await updateSystemConfig({
          id: meta.id,
          configKey: meta.configKey,
          configValue: update.value
        })
      }
    }
    ElMessage.success('保存成功')
    // 重新加载以刷新元数据 id
    await loadConfig()
    logOperation('config_update', '修改系统参数配置')
  } catch (e) {
  } finally {
    configSaving.value = false
  }
}

// ============ 告警规则（与之前相同） ============
const ruleLoading = ref(false)
const ruleSubmitting = ref(false)
const rules = ref([])

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

// 阈值表达式编辑器
const FIELD_UNIT_MAP = THRESHOLD_FIELD_OPTIONS.reduce((m, f) => {
  m[f.value] = f.unit
  return m
}, {})

const conditions = ref([])
const timeConstraint = ref('')

function makeEmptyCondition() {
  return {
    field: '电压',
    operator: '<',
    value: 0,
    useRated: false,
    multiplier: 1.2,
    unit: 'V'
  }
}

function onFieldChange(idx) {
  const cond = conditions.value[idx]
  cond.unit = FIELD_UNIT_MAP[cond.field] || ''
  if (cond.field !== '功率') cond.useRated = false
}

function addCondition() {
  conditions.value.push(makeEmptyCondition())
}

function removeCondition(idx) {
  conditions.value.splice(idx, 1)
}

function parseThreshold(str) {
  if (!str) return { conditions: [makeEmptyCondition()], timeConstraint: '' }
  let tc = ''
  if (str.includes('(白天)')) tc = '(白天)'
  else if (str.includes('(夜间)')) tc = '(夜间)'
  const cleanStr = str.replace(/\(白天\)|\(夜间\)/g, '').trim()
  const parts = cleanStr.split('或').map((s) => s.trim()).filter(Boolean)
  const conds = parts.map((part) => {
    const cond = makeEmptyCondition()
    const m = part.match(
      /^(电压|温度|电流|功率|照度|湿度|亮度|关闭时间)([><]=?|=)(额定值|\d+(?:\.\d+)?)([*×]\d+(?:\.\d+)?)?(V|°C|A|W|lux|%RH|%|h)?$/
    )
    if (m) {
      cond.field = m[1]
      cond.operator = m[2]
      cond.unit = m[5] || FIELD_UNIT_MAP[m[1]] || ''
      if (m[3] === '额定值') {
        cond.useRated = true
        cond.multiplier = m[4] ? parseFloat(m[4].replace(/[*×]/, '')) : 1.0
      } else {
        cond.useRated = false
        cond.value = parseFloat(m[3])
      }
    }
    return cond
  })
  return {
    conditions: conds.length > 0 ? conds : [makeEmptyCondition()],
    timeConstraint: tc
  }
}

function buildThreshold(conds, tc) {
  const parts = conds.map((c) => {
    const val = c.useRated
      ? `额定值${c.multiplier !== 1.0 ? '*' + c.multiplier.toFixed(1) : ''}`
      : String(c.value)
    return `${c.field}${c.operator}${val}${c.unit || ''}`
  })
  return parts.join('或') + (tc || '')
}

const thresholdPreview = computed(() => {
  const valid = conditions.value.filter((c) => c.field && c.operator)
  if (valid.length === 0) return ''
  return buildThreshold(valid, timeConstraint.value)
})

// 规则弹窗
const ruleDialogVisible = ref(false)
const ruleIsEdit = ref(false)
const ruleFormRef = ref()
const ruleForm = reactive({
  id: undefined,
  ruleType: undefined,
  ruleName: '',
  threshold: '',
  enabled: true,
  description: ''
})

const ruleRules = {
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  threshold: [{ required: true, message: '请填写阈值条件', trigger: 'change' }]
}

function resetRuleForm() {
  Object.assign(ruleForm, {
    id: undefined,
    ruleType: undefined,
    ruleName: '',
    threshold: '',
    enabled: true,
    description: ''
  })
  conditions.value = [makeEmptyCondition()]
  timeConstraint.value = ''
  ruleFormRef.value?.clearValidate()
}

function onRuleTypeChange(val) {
  if (!ruleForm.ruleName && val) {
    ruleForm.ruleName = ALERT_RULE_TYPE_MAP[val]?.label || ''
  }
}

function openRuleDialog(row) {
  ruleIsEdit.value = !!row
  if (row) {
    Object.assign(ruleForm, {
      id: row.id,
      ruleType: row.ruleType,
      ruleName: row.ruleName || '',
      threshold: row.threshold || '',
      enabled: row.enabled,
      description: row.description || ''
    })
    const parsed = parseThreshold(row.threshold)
    conditions.value = parsed.conditions
    timeConstraint.value = parsed.timeConstraint
  } else {
    resetRuleForm()
  }
  ruleDialogVisible.value = true
}

async function onRuleSubmit() {
  const valid = conditions.value.filter((c) => c.field && c.operator)
  if (valid.length === 0) {
    ElMessage.warning('请至少添加一个阈值条件')
    return
  }
  ruleForm.threshold = buildThreshold(valid, timeConstraint.value)
  try {
    await ruleFormRef.value.validate()
  } catch (e) {
    return
  }
  ruleSubmitting.value = true
  try {
    const ruleLabel = ruleForm.ruleName || ALERT_RULE_TYPE_MAP[ruleForm.ruleType]?.label || ruleForm.ruleType
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
  } finally {
    ruleSubmitting.value = false
  }
}

async function onRuleDelete(row) {
  const ruleLabel = row.ruleName || ALERT_RULE_TYPE_MAP[row.ruleType]?.label || row.ruleType
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
  }
}

async function onToggleEnabled(row) {
  try {
    await updateAlertRule({ ...row })
    ElMessage.success(row.enabled ? '已启用' : '已停用')
    const ruleLabel = row.ruleName || ALERT_RULE_TYPE_MAP[row.ruleType]?.label || row.ruleType
    logOperation('config_update', `${row.enabled ? '启用' : '停用'}告警规则：${ruleLabel}`)
  } catch (e) {
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

.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}

.threshold-editor {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.or-label {
  color: #f56c6c;
  font-weight: 600;
  font-size: 14px;
  margin-right: 4px;
}

.cond-unit {
  color: #909399;
  font-size: 13px;
  min-width: 36px;
}

.time-constraint-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.tc-label {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}

.threshold-preview {
  margin-top: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px dashed #409eff;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-label {
  color: #909399;
  font-size: 13px;
  white-space: nowrap;
}

.preview-code {
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 4px;
}
</style>