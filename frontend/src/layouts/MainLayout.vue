<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <el-icon class="logo-icon"><Sunny /></el-icon>
        <span v-show="!collapsed" class="logo-text">智慧路灯</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        :default-openeds="openedMenus"
        background-color="#001529"
        text-color="#b7c0cd"
        active-text-color="#fff"
        router
        unique-opened
      >
        <template v-for="group in visibleMenu" :key="group.title">
          <!-- 分组 -->
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
          <!-- 单项 -->
          <el-menu-item v-else :index="group.path">
            <el-icon><component :is="group.icon" /></el-icon>
            <template #title>{{ group.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentGroup }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="unhandledCount" :hidden="unhandledCount === 0" :max="99">
            <el-icon class="bell-icon" @click="$router.push('/alerts')"><Bell /></el-icon>
          </el-badge>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-avatar :size="30" class="avatar">
                {{ displayName.charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="username">{{ displayName }}</span>
              <el-tag size="small" :type="roleTagType" effect="plain">
                {{ roleLabel }}
              </el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="assistant" icon="ChatDotRound">AI 运维助手</el-dropdown-item>
                <el-dropdown-item command="logout" icon="SwitchButton" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
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
let pollTimer = null

const isAdmin = computed(() => userStore.isAdmin)
const role = computed(() => userStore.user?.role || 'operator')
const displayName = computed(() => userStore.displayName)
const roleLabel = computed(() => USER_ROLE_MAP[role.value]?.label || role.value)
const roleTagType = computed(() => USER_ROLE_MAP[role.value]?.type || 'primary')

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
    title: '实时监测',
    icon: 'Monitor',
    roles: ['admin', 'municipal', 'operator'],
    children: [
      { title: '设备监控', path: '/monitor/realtime', icon: 'Aim' },
      { title: '传感器数据', path: '/monitor/sensor-data', icon: 'DataLine' }
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
    title: '告警管理',
    path: '/alerts',
    icon: 'Bell',
    roles: ['admin', 'municipal', 'operator']
  },
  {
    title: '碳减排分析',
    path: '/carbon',
    icon: 'TrendCharts',
    roles: ['admin', 'municipal']
  },
  {
    title: 'AI 智能中心',
    icon: 'MagicStick',
    roles: ['admin', 'municipal', 'operator'],
    children: [
      { title: 'AI 预测调光', path: '/ai/predict', icon: 'MagicStick' },
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
    // 新增告警弹窗提醒
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
  }
}

onMounted(() => {
  fetchUnhandled()
  pollTimer = setInterval(fetchUnhandled, 30000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background-color: #001529;
  transition: width 0.25s;
  overflow-x: hidden;
  overflow-y: auto;
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
}

.logo-icon {
  font-size: 26px;
  color: #f5a623;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.aside :deep(.el-sub-menu__title:hover),
.aside :deep(.el-menu-item:hover) {
  background-color: #1d2b3f !important;
}

.aside :deep(.el-menu-item.is-active) {
  background-color: var(--primary) !important;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 16px;
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
  background-color: var(--primary);
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
