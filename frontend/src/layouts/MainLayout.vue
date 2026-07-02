<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <el-icon class="logo-icon"><Sunny /></el-icon>
        <span v-show="!collapsed" class="logo-text">智慧路灯</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#001529"
        text-color="#b7c0cd"
        active-text-color="#fff"
        router
        unique-opened
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-menu-item :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
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
              <el-tag size="small" :type="isAdmin ? 'danger' : 'primary'" effect="plain">
                {{ isAdmin ? '管理员' : '运维' }}
              </el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
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
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getUnhandledCount } from '@/api/alert'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const unhandledCount = ref(0)
let pollTimer = null

const isAdmin = computed(() => userStore.isAdmin)
const displayName = computed(() => userStore.displayName)

const menuItems = computed(() => {
  const base = [
    { path: '/dashboard', title: '控制台', icon: 'Odometer' },
    { path: '/lights', title: '路灯管理', icon: 'Sunny' },
    { path: '/sensor-data', title: '传感器数据', icon: 'DataLine' },
    { path: '/alerts', title: '报警管理', icon: 'Bell' }
  ]
  if (isAdmin.value) {
    base.push({ path: '/users', title: '用户管理', icon: 'User' })
  }
  return base
})

const activeMenu = computed(() => {
  // 详情页高亮父级菜单
  if (route.path.startsWith('/lights')) return '/lights'
  return route.path
})

const currentTitle = computed(() => route.meta?.title || '')

async function fetchUnhandled() {
  try {
    const res = await getUnhandledCount()
    unhandledCount.value = res.data || 0
  } catch (e) {
    // 静默处理
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning'
    })
      .then(() => {
        userStore.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
      })
      .catch(() => {})
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
  overflow: hidden;
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
