<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">阈值联动控制</h2>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="loadConfig">重新加载</el-button>
      </div>
    </div>

    <!-- 说明卡片 -->
    <el-card shadow="never" class="info-card">
      <template #header>
        <div class="panel-header">
          <el-icon><InfoFilled /></el-icon>
          <span>联动逻辑说明</span>
        </div>
      </template>
      <ul class="logic-list">
        <li>系统按 <b>检测周期</b> 读取光照传感器数据，自动联动路灯开关与亮度。</li>
        <li>当实测光照 <b>低于某个开灯阈值</b> 时，自动开灯并设置为对应亮度。</li>
        <li>当实测光照 <b>高于「关灯光照阈值」</b> 时，自动关灯以节约能源。</li>
        <li>可配置多档开灯阈值，系统自动匹配最高匹配档位对应的亮度。</li>
        <li>通过右上角总开关可一键启停整套联动；停用后所有自动联动立即失效。</li>
      </ul>
    </el-card>

    <!-- 总开关 -->
    <el-card shadow="never" class="switch-card">
      <div class="switch-row">
        <div class="switch-info">
          <el-icon><Connection /></el-icon>
          <span class="switch-title">阈值联动总开关</span>
          <el-tag v-if="config.enabled" type="success" size="small">运行中</el-tag>
          <el-tag v-else type="info" size="small">已停用</el-tag>
        </div>
        <el-switch
          v-model="config.enabled"
          :loading="toggling"
          @change="onToggleEnabled"
        />
      </div>
    </el-card>

    <!-- 配置表单 -->
    <el-card shadow="never" class="form-card">
      <template #header>
        <div class="panel-header">
          <el-icon><Setting /></el-icon>
          <span>阈值与调光配置</span>
        </div>
      </template>
      <el-form ref="formRef" :model="config" label-width="160px" style="max-width: 640px">
        <el-form-item label="关灯光照阈值(lux)">
          <el-input-number v-model="config.lightOffThreshold" :min="0" :step="5" controls-position="right" style="width: 220px" />
          <span class="form-tip">高于此值自动关灯</span>
        </el-form-item>
        <el-divider content-position="left">开灯设置</el-divider>
        <div class="segment-list">
          <div
            v-for="(seg, index) in config.segments"
            :key="index"
            class="segment-item"
          >
            <div class="segment-index">{{ index + 1 }}</div>
            <div class="segment-fields">
              <el-form-item label="光照阈值(lux)" :prop="'segments.' + index + '.threshold'">
                <el-input-number v-model="seg.threshold" :min="0" :step="5" controls-position="right" style="width: 220px" />
                <span class="form-tip">低于此值开灯</span>
              </el-form-item>
              <el-form-item label="亮度(%)" :prop="'segments.' + index + '.brightness'">
                <el-slider v-model="seg.brightness" :min="0" :max="100" show-input style="width: 380px" />
              </el-form-item>
            </div>
            <el-button
              v-if="config.segments.length > 1"
              type="danger"
              size="small"
              text
              @click="removeSegment(index)"
            >删除</el-button>
          </div>
        </div>
        <el-button type="primary" size="small" plain @click="addSegment">+ 添加档位</el-button>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="onSave">保存配置</el-button>
          <el-button @click="loadConfig">重置为已保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
defineOptions({ name: 'ThresholdControl' })
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, InfoFilled, Connection, Setting } from '@element-plus/icons-vue'
import {
  getThresholdConfig,
  updateThresholdConfig,
  toggleThreshold
} from '@/api/control'
import { logOperation } from '@/utils/log'

const loading = ref(false)
const saving = ref(false)
const toggling = ref(false)
const formRef = ref()

const config = reactive({
  enabled: false,
  lightOffThreshold: 100,
  segments: [
    { threshold: 30, brightness: 100 }
  ]
})

function addSegment() {
  const lastThreshold = config.segments.length > 0
    ? config.segments[config.segments.length - 1].threshold
    : 50
  config.segments.push({
    threshold: Math.max(0, lastThreshold + 20),
    brightness: 50
  })
}

function removeSegment(index) {
  if (config.segments.length <= 1) return
  config.segments.splice(index, 1)
}

async function loadConfig() {
  loading.value = true
  try {
    const res = await getThresholdConfig()
    if (res.data) {
      const data = res.data
      config.enabled = !!data.enabled
      config.lightOffThreshold = data.lightOffThreshold ?? 100
      if (Array.isArray(data.segments) && data.segments.length > 0) {
        config.segments = data.segments.map(s => ({
          threshold: s.threshold ?? 0,
          brightness: s.brightness ?? 100
        }))
      } else {
        config.segments = [{ threshold: 30, brightness: 100 }]
      }
    }
  } catch (e) {
    // 后端接口缺失：保留默认配置，错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

// 总开关启停
async function onToggleEnabled(val) {
  toggling.value = true
  try {
    await toggleThreshold(val)
    ElMessage.success(val ? '阈值联动已启用' : '阈值联动已停用')
    await logOperation('threshold_update', `${val ? '启用' : '停用'}阈值联动总开关`, '成功')
  } catch (e) {
    config.enabled = !val // 回滚
    await logOperation('threshold_update', `${val ? '启用' : '停用'}阈值联动总开关`, '失败')
  } finally {
    toggling.value = false
  }
}

// 保存配置
async function onSave() {
  saving.value = true
  try {
    const payload = {
      enabled: config.enabled,
      lightOffThreshold: config.lightOffThreshold,
      segments: config.segments
    }
    await updateThresholdConfig(payload)
    ElMessage.success('阈值配置已保存')
    await logOperation('threshold_update', '更新阈值联动配置', '成功')
  } catch (e) {
    await logOperation('threshold_update', '更新阈值联动配置', '失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.info-card,
.switch-card,
.form-card {
  margin-bottom: 16px;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.logic-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
  color: #606266;
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.switch-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.switch-title {
  font-weight: 600;
}
.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.segment-list {
  margin-bottom: 12px;
}
.segment-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.segment-index {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #409eff;
  border-radius: 50%;
  margin-top: 4px;
}
.segment-fields {
  flex: 1;
}
.segment-fields :deep(.el-form-item) {
  margin-bottom: 12px;
}
.segment-fields :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
</style>
