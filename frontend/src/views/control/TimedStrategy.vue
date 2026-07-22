<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">照明策略</h2>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增策略</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="table-card">
      <el-table :data="tableData" stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="STRATEGY_TYPE_MAP[row.type]?.type" size="small">
              {{ STRATEGY_TYPE_MAP[row.type]?.label || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="适用时间" min-width="220">
          <template #default="{ row }">
            <!-- 时间段类型：显示完整时间段（日期 + 时段） -->
            <template v-if="row.type === 'timed'">
              <div class="time-slot">
                <span class="time-range">
                  <template v-if="row.startDate || row.endDate">
                    {{ row.startDate || '...' }} ~ {{ row.endDate || '...' }}
                  </template>
                </span>
                <span class="time-range time-daily" v-if="row.startTime && row.endTime">
                  <el-icon><Clock /></el-icon>
                  {{ row.startTime }} ~ {{ row.endTime }}
                </span>
              </div>
            </template>
            <!-- 默认类型：显示适用星期 + 每日时段 -->
            <template v-else>
              <div class="weekday-list">
                <template v-if="row.weekdays && row.weekdays.length">
                  <el-tag
                    v-for="w in row.weekdays"
                    :key="w"
                    size="small"
                    style="margin-right: 4px"
                  >{{ weekdayFullLabel(w) }}</el-tag>
                </template>
                <span v-else class="text-muted">每天</span>
              </div>
              <div class="time-daily" v-if="row.startTime && row.endTime">
                <el-icon><Clock /></el-icon>
                {{ row.startTime }} ~ {{ row.endTime }}
                <el-tag v-if="isCrossDay(row.startTime, row.endTime)" size="small" type="warning" style="margin-left:4px">次日</el-tag>
              </div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="目标亮度" width="100">
          <template #default="{ row }">{{ row.brightness ?? 0 }}%</template>
        </el-table-column>
        <el-table-column label="适用区域" width="180">
          <template #default="{ row }">
            <template v-if="row.groups?.length">
              <template v-for="(g, i) in row.groups" :key="i">
                <template v-if="g.district">{{ g.district }}</template>
                <template v-if="g.roads?.length">
                  <span v-if="g.district" class="text-muted"> · </span>
                  {{ g.roads.join('、') }}
                </template>
                <template v-if="i < row.groups.length - 1">；</template>
              </template>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
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
        <el-form-item v-if="form.type !== 'timed'" label="适用星期" prop="weekdays">
          <el-checkbox-group v-model="form.weekdays">
            <el-checkbox
              v-for="w in WEEKDAY_OPTIONS"
              :key="w.value"
              :value="w.value"
            >{{ w.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item v-if="form.type === 'timed'" label="开始日期" prop="startDate">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            placeholder="选择开始日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="form.type === 'timed'" label="结束日期" prop="endDate">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            placeholder="选择结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
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
          <el-slider v-model="form.brightness" :min="0" :max="100" show-input style="width: 100%" :disabled="form.useDynamicBrightness" />
        </el-form-item>
        <el-form-item label="动态亮度">
          <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
            <el-switch v-model="form.useDynamicBrightness" />
            <span class="text-muted" style="font-size: 12px;">
              {{ form.useDynamicBrightness ? '根据实时光照自动调节亮度' : '使用上方固定亮度值' }}
            </span>
          </div>
        </el-form-item>
        <el-form-item label="适用区域">
          <div class="apply-groups">
            <div
              v-for="(group, index) in form.applyGroups"
              :key="index"
              class="apply-group-item"
            >
              <div class="group-fields">
                <el-select
                  v-model="group.district"
                  clearable
                  filterable
                  placeholder="选择动物园区"
                  style="width: 35%"
                >
                  <el-option
                    v-for="o in DISTRICT_OPTIONS"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                    :disabled="isDistrictDisabled(o.value, index)"
                  />
                </el-select>
              </div>
              <!-- 分组级阈值配置：动态亮度开启时直接展示 -->
              <div v-if="form.useDynamicBrightness" class="group-threshold-section">
                <div class="group-threshold-detail" style="margin-top: 0; padding-top: 10px;">
                  <div class="threshold-row">
                    <span class="threshold-label">关灯阈值 (lux)：</span>
                    <el-input-number
                      v-model="group.lightOffThreshold"
                      :min="0"
                      :max="9999"
                      :step="10"
                      size="small"
                      style="width: 140px;"
                    />
                  </div>
                  <div class="threshold-segments">
                    <span class="threshold-label">亮度分段：</span>
                    <div style="display: flex; flex-direction: column; gap: 6px; flex: 1;">
                      <div
                        v-for="(seg, idx) in group.brightnessSegments"
                        :key="idx"
                        style="display: flex; gap: 6px; align-items: center;"
                      >
                        <span style="font-size: 11px; white-space: nowrap;">光照 ≤</span>
                        <el-input-number
                          v-model="seg.threshold"
                          :min="0"
                          :max="9999"
                          :step="10"
                          size="small"
                          style="width: 100px;"
                        />
                        <span style="font-size: 11px; white-space: nowrap;">lux →</span>
                        <el-input-number
                          v-model="seg.brightness"
                          :min="0"
                          :max="100"
                          :step="5"
                          size="small"
                          style="width: 80px;"
                        />
                        <span style="font-size: 11px;">%</span>
                        <el-button
                          v-if="group.brightnessSegments.length > 1"
                          :icon="Delete"
                          circle
                          size="small"
                          type="danger"
                          @click="group.brightnessSegments.splice(idx, 1)"
                        />
                      </div>
                      <el-button type="primary" size="small" @click="group.brightnessSegments.push({ threshold: 30, brightness: 100 })" style="align-self: flex-start;">
                        + 添加分段
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
              <div v-if="form.applyGroups.length > 1" class="group-actions">
                <el-button type="danger" size="small" @click="removeApplyGroup(index)">删除</el-button>
              </div>
            </div>
            <el-button type="primary" size="small" @click="addApplyGroup" class="add-group-btn">
              + 添加行政区
            </el-button>
          </div>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Clock, Delete } from '@element-plus/icons-vue'
import {
  getStrategyPage,
  addStrategy,
  updateStrategy,
  deleteStrategy,
  toggleStrategy
} from '@/api/control'
import { getDistricts, getAllLights } from '@/api/light'
import { STRATEGY_TYPE_MAP, WEEKDAY_OPTIONS } from '@/utils/constants'
import { logOperation } from '@/utils/log'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const DISTRICT_OPTIONS = ref([])
const districtRoadMap = ref({})

const query = reactive({
  pageNum: 1,
  pageSize: 10
})

function weekdayLabel(val) {
  return WEEKDAY_OPTIONS.find((o) => o.value === val)?.label || val
}

function weekdayFullLabel(val) {
  return weekdayLabel(val)
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
  type: 'default',
  weekdays: [],
  startDate: '',
  endDate: '',
  startTime: '18:00:00',
  endTime: '06:00:00',
  brightness: 80,
  useDynamicBrightness: false,
  applyGroups: [],
  enabled: true
})



function addApplyGroup() {
  form.applyGroups.push({
    district: '',
    roads: [],
    lightOffThreshold: 100,
    brightnessSegments: [{ threshold: 30, brightness: 100 }]
  })
}

function removeApplyGroup(index) {
  form.applyGroups.splice(index, 1)
}

function isDistrictDisabled(districtValue, currentIndex) {
  return form.applyGroups.some((g, idx) => idx !== currentIndex && g.district === districtValue)
}

const rules = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择策略类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  startDate: [{
    validator: (rule, value, callback) => {
      if (form.type === 'timed' && !value) callback(new Error('时间段策略必须选择开始日期'))
      else callback()
    }, trigger: 'change'
  }],
  endDate: [{
    validator: (rule, value, callback) => {
      if (form.type === 'timed' && !value) callback(new Error('时间段策略必须选择结束日期'))
      else if (form.type === 'timed' && value && form.startDate && value < form.startDate) callback(new Error('结束日期不能早于开始日期'))
      else callback()
    }, trigger: 'change'
  }],
  brightness: [{
    validator: (rule, value, callback) => {
      if (value == null || value < 0 || value > 100) callback(new Error('亮度必须在0-100之间'))
      else callback()
    }, trigger: 'change'
  }]
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    let applyGroups = []
    // 直接解析 groups 结构，完美保留行政区-路段配对
    const groups = Array.isArray(row.groups) ? row.groups : []
    groups.forEach(g => {
      applyGroups.push({
        district: g.district || '',
        roads: Array.isArray(g.roads) ? [...g.roads] : [],
        lightOffThreshold: g.lightOffThreshold ?? 100,
        brightnessSegments: (g.brightnessSegments && g.brightnessSegments.length > 0)
          ? g.brightnessSegments.map(s => ({ threshold: s.threshold ?? 30, brightness: s.brightness ?? 100 }))
          : [{ threshold: 30, brightness: 100 }]
      })
    })
    if (applyGroups.length === 0) {
      applyGroups.push({ district: '', roads: [], lightOffThreshold: 100, brightnessSegments: [{ threshold: 30, brightness: 100 }] })
    }
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      type: row.type || 'default',
      weekdays: Array.isArray(row.weekdays) ? [...row.weekdays] : [],
      startDate: row.startDate || '',
      endDate: row.endDate || '',
      startTime: row.startTime || '18:00:00',
      endTime: row.endTime || '06:00:00',
      brightness: row.brightness ?? 80,
      useDynamicBrightness: !!row.useDynamicBrightness,
      applyGroups,
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
    type: 'default',
    weekdays: [],
    startDate: '',
    endDate: '',
    startTime: '18:00:00',
    endTime: '06:00:00',
    brightness: 80,
    useDynamicBrightness: false,
    applyGroups: [{ district: '', roads: [], lightOffThreshold: 100, brightnessSegments: [{ threshold: 30, brightness: 100 }] }],
    enabled: true
  })
  formRef.value?.clearValidate()
}

function parseTimeStr(t) {
  if (!t) return null
  const parts = t.split(':').map(Number)
  if (parts.length < 2 || parts.some(isNaN)) return null
  return parts[0] * 60 + parts[1]
}

/** 判断时间段是否跨天（start >= end 即视为跨天） */
function isCrossDay(startTime, endTime) {
  if (!startTime || !endTime) return false
  return startTime >= endTime
}

function isTimeRangeOverlap(start1, end1, start2, end2) {
  const s1 = parseTimeStr(start1)
  const e1 = parseTimeStr(end1)
  const s2 = parseTimeStr(start2)
  const e2 = parseTimeStr(end2)
  if (s1 === null || e1 === null || s2 === null || e2 === null) return false

  const wraps1 = s1 >= e1
  const wraps2 = s2 >= e2

  const toPoints = (s, e, wraps) => {
    if (wraps) return [[s, 24 * 60], [0, e]]
    return [[s, e]]
  }

  const segs1 = toPoints(s1, e1, wraps1)
  const segs2 = toPoints(s2, e2, wraps2)

  for (const [a1, a2] of segs1) {
    for (const [b1, b2] of segs2) {
      if (a1 < b2 && b1 < a2) return true
    }
  }
  return false
}


function isGroupOverlap(group, otherDistrict, otherRoads) {
  const formDistrict = group.district || ''
  const formRoads = group.roads || []

  if (!formDistrict && !formRoads.length) {
    return true
  }
  if (!otherDistrict && !otherRoads.length) {
    return true
  }

  if (formRoads.length > 0 && otherRoads.length > 0) {
    if (formDistrict && otherDistrict && formDistrict !== otherDistrict) {
      return false
    }
    return formRoads.some(r => otherRoads.includes(r))
  }

  if (formDistrict && !formRoads.length) {
    if (otherRoads.length > 0) {
      return otherRoads.some(r => {
        const d = districtRoadMap.value[formDistrict] || []
        return d.includes(r)
      })
    }
    return formDistrict === otherDistrict
  }

  if (otherDistrict && !otherRoads.length) {
    if (formRoads.length > 0) {
      return formRoads.some(r => {
        const d = districtRoadMap.value[otherDistrict] || []
        return d.includes(r)
      })
    }
    return formDistrict === otherDistrict
  }

  return false
}

function isRoadOverlap(other) {
  const otherGroups = Array.isArray(other.groups) ? other.groups : []

  if (!form.applyGroups || form.applyGroups.length === 0) {
    return true
  }

  if (otherGroups.length === 0) return true

  for (const og of otherGroups) {
    const oDistrict = og.district || ''
    const oRoads = Array.isArray(og.roads) ? og.roads : []
    for (const group of form.applyGroups) {
      if (isGroupOverlap(group, oDistrict, oRoads)) return true
    }
  }
  return false
}

function isDateOverlap(other) {
  const fType = form.type
  const oType = other.type

  if (fType === 'default' && oType === 'default') {
    const fDays = form.weekdays || []
    const oDays = other.weekdays || []
    if (fDays.length === 0 || oDays.length === 0) return true
    return fDays.some(d => oDays.includes(d))
  }

  if (fType === 'timed' && oType === 'timed') {
    const fStart = form.startDate
    const fEnd = form.endDate
    const oStart = other.startDate
    const oEnd = other.endDate
    if (!fStart || !fEnd || !oStart || !oEnd) return false
    return fStart <= oEnd && oStart <= fEnd
  }

  if (fType === 'timed' && oType === 'default') {
    return true
  }
  if (fType === 'default' && oType === 'timed') {
    return true
  }

  return false
}

function getConflictDesc(other) {
  const parts = []
  if (other.type === 'default') {
    if (other.weekdays?.length) {
      parts.push(other.weekdays.map(d => weekdayLabel(d)).join('、'))
    } else {
      parts.push('每天')
    }
  }
  if (other.type === 'timed' && other.startDate && other.endDate) {
    parts.push(`${other.startDate} ~ ${other.endDate}`)
  }
  if (other.startTime && other.endTime) {
    parts.push(`${other.startTime} ~ ${other.endTime}`)
  }
  if (other.groups?.length) {
    const groupDesc = other.groups.map(g => {
      const items = []
      if (g.district) items.push(g.district)
      if (g.roads?.length) items.push(g.roads.join('、'))
      return items.join(' · ')
    }).join('；')
    parts.push(groupDesc)
  }
  return parts.join('；')
}

function checkConflict() {
  const otherStrategies = tableData.value.filter(s =>
    s.id !== form.id &&
    s.enabled !== false
  )

  const sameTypeConflicts = []
  const crossTypeConflicts = []

  for (const other of otherStrategies) {
    if (!isRoadOverlap(other)) continue
    if (!isDateOverlap(other)) continue
    if (!isTimeRangeOverlap(form.startTime, form.endTime, other.startTime, other.endTime)) continue

    const desc = getConflictDesc(other)
    if (other.type === form.type) {
      sameTypeConflicts.push({ name: other.name, desc })
    } else {
      crossTypeConflicts.push({ name: other.name, desc, type: other.type })
    }
  }

  if (sameTypeConflicts.length > 0) {
    const typeLabel = form.type === 'default' ? '默认' : '时间段'
    const conflictInfo = sameTypeConflicts.map(c => `策略「${c.name}」（${c.desc}）`).join('\n')
    return {
      level: 'block',
      message: `与以下已启用的${typeLabel}策略存在冲突（同路段同时段）：\n\n${conflictInfo}\n\n同级策略不允许冲突，请调整时间或路段。`
    }
  }

  if (crossTypeConflicts.length > 0) {
    const higherType = form.type === 'timed' ? '时间段' : '默认'
    const conflictInfo = crossTypeConflicts.map(c => `策略「${c.name}」（${c.desc}）`).join('\n')
    const tip = form.type === 'timed'
      ? '提示：时间段策略优先级高于默认策略，保存后将在重叠时段覆盖默认策略。'
      : '提示：默认策略优先级低于时间段策略，重叠时段将由时间段策略生效。'
    return {
      level: 'warn',
      message: `与以下已启用的${higherType}策略存在时段重叠：\n\n${conflictInfo}\n\n${tip}`
    }
  }

  return { level: 'none' }
}

async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  const conflictCheck = checkConflict()
  if (conflictCheck.level === 'block') {
    ElMessage.warning(conflictCheck.message)
    return
  }
  if (conflictCheck.level === 'warn') {
    try {
      await ElMessageBox.confirm(conflictCheck.message, '策略冲突提醒', {
        confirmButtonText: '仍要保存',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: false
      })
    } catch (e) {
      return
    }
  }

  submitting.value = true
  try {
    const groups = form.applyGroups
      .map(g => {
        const group = {
          district: g.district || '',
          roads: g.roads || []
        }
        // 动态亮度开启时，始终携带分组级阈值配置
        if (form.useDynamicBrightness) {
          group.lightOffThreshold = g.lightOffThreshold
          group.brightnessSegments = g.brightnessSegments
        }
        return group
      })

    const basePayload = {
      id: form.id,
      name: form.name,
      type: form.type,
      startTime: form.startTime,
      endTime: form.endTime,
      brightness: form.brightness,
      useDynamicBrightness: form.useDynamicBrightness,
      enabled: form.enabled,
      groups
    }
    if (form.type === 'default') {
      basePayload.weekdays = form.weekdays || []
    }
    if (form.type === 'timed') {
      basePayload.startDate = form.startDate || ''
      basePayload.endDate = form.endDate || ''
    }
    const payload = basePayload
    if (isEdit.value) {
      await updateStrategy(payload)
      ElMessage.success('更新成功')
      await logOperation('strategy_update', `修改策略「${form.name}」`, '成功')
    } else {
      const { id, ...createPayload } = payload
      await addStrategy(createPayload)
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

async function loadOptions() {
  try {
    const [districtsRes, lightsRes] = await Promise.all([
      getDistricts(),
      getAllLights()
    ])

    DISTRICT_OPTIONS.value = (districtsRes.data || []).map(d => ({ value: d, label: d }))
    
    const map = {}
    const lights = lightsRes.data || []
    lights.forEach(light => {
      if (light.district && light.road) {
        if (!map[light.district]) {
          map[light.district] = []
        }
        if (!map[light.district].includes(light.road)) {
          map[light.district].push(light.road)
        }
      }
    })
    districtRoadMap.value = map
  } catch (e) {
    console.error('加载选项数据失败:', e)
  }
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped>
.time-slot {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.time-range {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.time-daily {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.time-daily .el-icon {
  font-size: 12px;
  color: #909399;
}

.weekday-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 2px;
}

.apply-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.apply-group-item {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}

.group-fields {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: flex-start;
}


.group-actions {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #dcdfe6;
}

.add-group-btn {
  margin-top: 4px;
  align-self: flex-start;
}

.group-threshold-section {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
}

.group-threshold-detail {
  margin-top: 8px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.threshold-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.threshold-label {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}

.threshold-segments {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
</style>

