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
        <li>当实测光照 <b>低于「开灯光照阈值」</b> 时，自动开灯并按调光曲线低档输出。</li>
        <li>当实测光照 <b>高于「关灯光照阈值」</b> 时，自动关灯以节约能源。</li>
        <li>调光曲线按低 / 中 / 高三档亮度分段输出，光照越低、亮度越高。</li>
        <li>通过右上角总开关可一键启停整套联动；停用后所有自动联动立即失效。</li>
      </ul>
    </el-card>

    <!-- 总开关 -->
    <el-card shadow="never" class="switch-card" v-loading="loading">
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
    <el-card shadow="never" class="form-card" v-loading="loading">
      <template #header>
        <div class="panel-header">
          <el-icon><Setting /></el-icon>
          <span>阈值与调光配置</span>
        </div>
      </template>
      <el-form ref="formRef" :model="config" label-width="160px" style="max-width: 640px">
        <el-form-item label="开灯光照阈值(lux)">
          <el-input-number v-model="config.lightOnThreshold" :min="0" :step="5" controls-position="right" style="width: 220px" />
          <span class="form-tip">低于此值自动开灯</span>
        </el-form-item>
        <el-form-item label="关灯光照阈值(lux)">
          <el-input-number v-model="config.lightOffThreshold" :min="0" :step="5" controls-position="right" style="width: 220px" />
          <span class="form-tip">高于此值自动关灯</span>
        </el-form-item>
        <el-divider content-position="left">调光曲线（分段亮度）</el-divider>
        <el-form-item label="低光照档亮度(%)">
          <el-slider v-model="config.lowBrightness" :min="0" :max="100" show-input style="width: 380px" />
        </el-form-item>
        <el-form-item label="中光照档亮度(%)">
          <el-slider v-model="config.midBrightness" :min="0" :max="100" show-input style="width: 380px" />
        </el-form-item>
        <el-form-item label="高光照档亮度(%)">
          <el-slider v-model="config.highBrightness" :min="0" :max="100" show-input style="width: 380px" />
        </el-form-item>
        <el-divider content-position="left">采集参数</el-divider>
        <el-form-item label="检测周期(秒)">
          <el-input-number v-model="config.detectionPeriod" :min="5" :step="5" controls-position="right" style="width: 220px" />
          <span class="form-tip">光照采样间隔</span>
        </el-form-item>
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
  lightOnThreshold: 30,
  lightOffThreshold: 100,
  lowBrightness: 100,
  midBrightness: 60,
  highBrightness: 30,
  detectionPeriod: 60
})

async function loadConfig() {
  loading.value = true
  try {
    const res = await getThresholdConfig()
    if (res.data) {
      Object.assign(config, res.data)
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
    await updateThresholdConfig({ ...config })
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
</style>
