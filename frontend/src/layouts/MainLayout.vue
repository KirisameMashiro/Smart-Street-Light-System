<template>
  <el-container class="layout">
    <!-- 桌面端侧边栏 -->
    <el-aside
      :width="isMobile ? '0' : (collapsed ? '64px' : '220px')"
      class="aside desktop-aside"
      :class="{ 'is-collapsed': collapsed }"
    >
      <div class="logo">
        <span class="logo-icon">🌿</span>
        <span v-show="!collapsed" class="logo-text">动物园照明</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        :default-openeds="openedMenus"
        background-color="transparent"
        text-color="rgba(255, 255, 255, 0.7)"
        active-text-color="#a5d6a7"
        router
        unique-opened
      >
        <template v-for="group in visibleMenu" :key="group.title">
          <el-sub-menu v-if="group.children" :index="group.title">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item
              v-for="item in group.children"
              :key="item.path"
              :index="item.path"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="group.path">
            <el-icon><component :is="group.icon" /></el-icon>
            <template #title>{{ group.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <!-- 移动端侧边栏抽屉 -->
    <div
      v-if="isMobile"
      class="mobile-drawer-mask"
      :class="{ open: mobileMenuOpen }"
      @click="mobileMenuOpen = false"
    >
      <div class="mobile-drawer" @click.stop>
        <div class="logo">
          <span class="logo-icon">🌿</span>
          <span class="logo-text">动物园照明</span>
        </div>
        <el-menu
          :default-active="activeMenu"
          background-color="transparent"
          text-color="rgba(255, 255, 255, 0.7)"
          active-text-color="#a5d6a7"
          router
          @select="mobileMenuOpen = false"
        >
          <template v-for="group in visibleMenu" :key="group.title">
            <el-sub-menu v-if="group.children" :index="group.title">
              <template #title>
                <el-icon><component :is="group.icon" /></el-icon>
                <span>{{ group.title }}</span>
              </template>
              <el-menu-item
                v-for="item in group.children"
                :key="item.path"
                :index="item.path"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <template #title>{{ item.title }}</template>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="group.path">
              <el-icon><component :is="group.icon" /></el-icon>
              <template #title>{{ group.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
    </div>

    <el-container>
      <el-header class="header" :class="{ 'is-mobile': isMobile }">
        <div class="header-left">
          <!-- 移动端汉堡菜单 -->
          <el-icon
            v-if="isMobile"
            class="collapse-btn mobile-menu-btn"
            @click="mobileMenuOpen = !mobileMenuOpen"
          >
            <Menu />
          </el-icon>
          <el-icon
            v-else
            class="collapse-btn"
            @click="collapsed = !collapsed"
          >
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb
            v-if="!isMobile"
            separator="/"
            class="hide-mobile"
          >
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentGroup }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
          <span v-else class="mobile-page-title">{{ currentTitle || '动物园照明' }}</span>
        </div>
        <div class="header-right">
          <el-badge :value="unhandledCount" :hidden="unhandledCount === 0" :max="99">
            <el-icon class="bell-icon" @click="$router.push('/alerts')"><Bell /></el-icon>
          </el-badge>
          <el-dropdown @command="onCommand" trigger="click">
        <span class="user-info">
          <el-avatar :size="isMobile ? 28 : 30" class="avatar">
            {{ displayName.charAt(0).toUpperCase() }}
          </el-avatar>
          <span v-if="!isMobile" class="username">{{ displayName }}</span>
          <el-tag v-if="!isMobile" size="small" :type="roleTagType" effect="plain">
            {{ roleLabel }}
          </el-tag>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="toggle-float-ball">
              <span>是否开启AI悬浮球</span>
              <el-switch :model-value="floatBallEnabled" size="small" @change="onToggleFloatBall" />
            </el-dropdown-item>
            <el-dropdown-item command="account" icon="UserFilled">账号管理</el-dropdown-item>
            <el-dropdown-item command="logout" icon="SwitchButton" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.fullPath" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage, ElNotification } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getUnhandledCount, getAlertPage } from '@/api/alert'
import { logOperation } from '@/utils/log'
import { USER_ROLE_MAP } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const unhandledCount = ref(0)
const mobileMenuOpen = ref(false)
const isMobile = ref(false)
const floatBallEnabled = ref(false)
let pollTimer = null

const cachedViews = [
  'Dashboard',
  'DeviceArchive',
  'DeviceLedger',
  'RealtimeMonitor',
  'SensorData',
  'IlluminanceTrend',
  'RemoteControl',
  'TimedStrategy',
  'ThresholdControl',
  'OperationLog',
  'Alerts',
  'FaultHandle',
  'CarbonAnalysis',
  'AiPredict',
  'AiAssistant',
  'AiKnowledge',
  'Users',
  'Account',
  'OperationAudit',
  'SystemConfig'
]

const isAdmin = computed(() => userStore.isAdmin)
const role = computed(() => userStore.user?.role || 'operator')
const displayName = computed(() => userStore.displayName)
const roleLabel = computed(() => USER_ROLE_MAP[role.value]?.label || role.value)
const roleTagType = computed(() => USER_ROLE_MAP[role.value]?.type || 'primary')

// 检测是否为移动端
function checkMobile() {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) {
    mobileMenuOpen.value = false
  }
}

// 菜单配置：roles 为空表示所有角色可见
const menuConfig = [
  { title: '控制台', path: '/dashboard', icon: 'Odometer' },
  {
    title: '设备管理',
    icon: 'Box',
    roles: ['admin', 'municipal', 'operator'],
    children: [
      { title: '设备档案', path: '/devices/archive', icon: 'Document' },
      { title: '台账统计', path: '/devices/ledger', icon: 'PieChart' }
    ]
  },
  {
    title: '设备监控',
    icon: 'Monitor',
    roles: ['admin', 'municipal', 'operator'],
    children: [
      { title: '实时监测', path: '/monitor/realtime', icon: 'Aim' },
      { title: '历史光照趋势', path: '/monitor/illuminance-trend', icon: 'TrendCharts' }
    ]
  },
  {
    title: '照明控制',
    icon: 'Switch',
    roles: ['admin', 'municipal', 'operator'],
    children: [
      { title: '远程控制', path: '/control/remote', icon: 'SetUp' },
      { title: '定时策略', path: '/control/strategy', icon: 'Clock' },
      { title: '阈值联动', path: '/control/threshold', icon: 'Connection' },
      { title: '操作日志', path: '/control/log', icon: 'List' }
    ]
  },
  {
    title: '广播管理',
    icon: 'Speaker',
    roles: ['admin', 'municipal', 'operator'],
    children: [
      { title: '广播设计', path: '/broadcast/design', icon: 'Microphone' },
      { title: '广播策略', path: '/broadcast/strategy', icon: 'Timer' }
    ]
  },
  {
    title: '告警管理',
    path: '/alerts',
    icon: 'Bell',
    roles: ['admin', 'municipal', 'operator']
  },
  {
    title: '故障处理',
    path: '/faults',
    icon: 'FirstAidKit',
    roles: ['admin', 'municipal', 'operator']
  },
  {
    title: '智能中心',
    icon: 'MagicStick',
    roles: ['admin', 'municipal', 'operator'],
    children: [
      { title: '预测调光', path: '/ai/predict', icon: 'MagicStick' },
      { title: 'AI 运维助手', path: '/ai/assistant', icon: 'ChatDotRound' },
      { title: '知识库管理', path: '/ai/knowledge', icon: 'Collection', roles: ['admin'] }
    ]
  },
  {
    title: '系统管理',
    icon: 'Setting',
    roles: ['admin'],
    children: [
      { title: '用户管理', path: '/system/users', icon: 'User' },
      { title: '操作审计', path: '/system/audit', icon: 'DocumentChecked' },
      { title: '参数配置', path: '/system/config', icon: 'Tools' }
    ]
  }
]

function itemVisible(item) {
  const roles = item.roles
  if (!roles || roles.length === 0) return true
  return roles.includes(role.value)
}

const visibleMenu = computed(() =>
  menuConfig
    .filter(itemVisible)
    .map((group) => {
      if (group.children) {
        return { ...group, children: group.children.filter(itemVisible) }
      }
      return group
    })
    .filter((g) => !g.children || g.children.length > 0)
)

const openedMenus = computed(() =>
  visibleMenu.value.filter((g) => g.children).map((g) => g.title)
)

const activeMenu = computed(() => {
  if (route.path.startsWith('/devices')) return route.path
  if (route.path.startsWith('/monitor')) return route.path
  if (route.path.startsWith('/control')) return route.path
  if (route.path.startsWith('/broadcast')) return route.path
  if (route.path.startsWith('/system')) return route.path
  if (route.path.startsWith('/ai')) return route.path
  return route.path
})

const currentTitle = computed(() => route.meta?.title || '')
const currentGroup = computed(() => {
  for (const g of visibleMenu.value) {
    if (g.children) {
      if (g.children.some((c) => c.path === route.path)) return g.title
    } else if (g.path === route.path) return g.title
  }
  return ''
})

async function fetchUnhandled() {
  try {
    const res = await getUnhandledCount()
    const cnt = res.data || 0
    if (cnt > unhandledCount.value && unhandledCount.value !== 0) {
      popupNewAlerts(cnt - unhandledCount.value)
    }
    unhandledCount.value = cnt
  } catch (e) {
    // 静默
  }
}

async function popupNewAlerts(n) {
  try {
    const res = await getAlertPage({ pageNum: 1, pageSize: n, status: 0 })
    const list = res.data?.records || []
    list.slice(0, 3).forEach((a) => {
      ElNotification({
        title: '新告警提醒',
        message: `${a.message || '设备异常'}（共 ${n} 条未处理）`,
        type: 'warning',
        duration: 6000,
        onClick: () => router.push('/alerts')
      })
    })
  } catch (e) {
    // ignore
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
      .then(() => {
        logOperation('user_login', `用户 ${displayName.value} 退出登录`)
        userStore.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
      })
      .catch(() => {})
  } else if (cmd === 'assistant') {
    router.push('/ai/assistant')
  } else if (cmd === 'account') {
    router.push('/system/account')
  }
}

function onToggleFloatBall(enabled) {
  floatBallEnabled.value = enabled
  localStorage.setItem('floatBallEnabled', enabled ? 'true' : 'false')
  window.dispatchEvent(new CustomEvent('float-ball-toggle', { detail: enabled }))
  if (enabled) {
    ElMessage.success('AI悬浮球已开启')
  } else {
    ElMessage.info('AI悬浮球已关闭')
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  floatBallEnabled.value = localStorage.getItem('floatBallEnabled') === 'true'
  fetchUnhandled()
  pollTimer = setInterval(fetchUnhandled, 30000)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.layout {
  height: 100vh;
  height: 100dvh;
  display: flex;
  overflow: hidden;
}

.aside {
  background: linear-gradient(180deg, #1b5e20 0%, #2e7d32 100%);
  transition: width 0.25s;
  overflow-x: hidden;
  overflow-y: auto;
  flex-shrink: 0;
}

.desktop-aside {
  flex-shrink: 0;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  overflow: hidden;
  white-space: nowrap;
  background: rgba(0, 0, 0, 0.1);
}

.logo-icon {
  font-size: 26px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
}

.aside :deep(.el-menu) {
  border-right: none;
  background-color: transparent;
}

.aside :deep(.el-sub-menu__title:hover),
.aside :deep(.el-menu-item:hover) {
  background-color: rgba(255, 255, 255, 0.1) !important;
}

.aside :deep(.el-menu-item.is-active) {
  background-color: rgba(76, 175, 80, 0.3) !important;
  color: #a5d6a7 !important;
}

.aside :deep(.el-sub-menu__title) {
  color: rgba(255, 255, 255, 0.8) !important;
}

.aside :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.7) !important;
}

.aside :deep(.el-menu-item:hover) {
  color: #fff !important;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 0 16px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #5a5e66;
}

.mobile-menu-btn {
  font-size: 22px;
  padding: 4px;
}

.mobile-page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.bell-icon {
  font-size: 20px;
  cursor: pointer;
  color: #5a5e66;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.avatar {
  background: linear-gradient(135deg, #2e7d32 0%, #43a047 100%);
  color: #fff;
  font-size: 14px;
}

.username {
  font-size: 14px;
  color: #303133;
}

.main {
  background-color: var(--bg);
  padding: 0;
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1;
  min-height: 0;
  -webkit-overflow-scrolling: touch;
  position: relative;
  height: auto !important;
}

:deep(.el-container) {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

:deep(.el-main) {
  padding: 0;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
}

/* 移动端抽屉菜单 */
.mobile-drawer-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.3s, visibility 0.3s;
}

.mobile-drawer-mask.open {
  opacity: 1;
  visibility: visible;
}

.mobile-drawer {
  position: absolute;
  top: 0;
  left: 0;
  width: 260px;
  height: 100%;
  background: linear-gradient(180deg, #1b5e20 0%, #2e7d32 100%);
  transform: translateX(-100%);
  transition: transform 0.3s ease;
  overflow-y: auto;
}

.mobile-drawer-mask.open .mobile-drawer {
  transform: translateX(0);
}

.mobile-drawer :deep(.el-menu) {
  border-right: none;
  background-color: transparent;
}

.mobile-drawer :deep(.el-sub-menu__title),
.mobile-drawer :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.8) !important;
}

/* 移动端顶部栏紧凑 */
.header.is-mobile {
  padding: 0 12px;
  height: 50px;
}

.header.is-mobile .header-right {
  gap: 12px;
}

.header.is-mobile .bell-icon {
  font-size: 18px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
