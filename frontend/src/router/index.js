import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '控制台', keepAlive: true }
      },
      // ===== 设备管理 =====
      {
        path: 'devices/archive',
        name: 'DeviceArchive',
        component: () => import('@/views/devices/DeviceArchive.vue'),
        meta: { title: '设备档案', keepAlive: true }
      },
      {
        path: 'devices/ledger',
        name: 'DeviceLedger',
        component: () => import('@/views/devices/DeviceLedger.vue'),
        meta: { title: '台账统计', keepAlive: true }
      },
      {
        path: 'devices/:id',
        name: 'LightDetail',
        component: () => import('@/views/LightDetail.vue'),
        meta: { title: '设备详情', hidden: true }
      },
      // ===== 实时监测 =====
      {
        path: 'monitor/realtime',
        name: 'RealtimeMonitor',
        component: () => import('@/views/monitor/RealtimeMonitor.vue'),
        meta: { title: '设备监控', keepAlive: true }
      },
      {
        path: 'monitor/sensor-data',
        name: 'SensorData',
        component: () => import('@/views/SensorData.vue'),
        meta: { title: '传感器数据', keepAlive: true }
      },
      // ===== 照明控制 =====
      {
        path: 'control/remote',
        name: 'RemoteControl',
        component: () => import('@/views/control/RemoteControl.vue'),
        meta: { title: '远程控制', keepAlive: true }
      },
      {
        path: 'control/strategy',
        name: 'TimedStrategy',
        component: () => import('@/views/control/TimedStrategy.vue'),
        meta: { title: '定时策略', keepAlive: true }
      },
      {
        path: 'control/threshold',
        name: 'ThresholdControl',
        component: () => import('@/views/control/ThresholdControl.vue'),
        meta: { title: '阈值联动', keepAlive: true }
      },
      {
        path: 'control/log',
        name: 'OperationLog',
        component: () => import('@/views/control/OperationLog.vue'),
        meta: { title: '操作日志', keepAlive: true }
      },
      // ===== 告警管理 =====
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/views/Alerts.vue'),
        meta: { title: '报警管理', keepAlive: true }
      },
      // ===== 碳减排分析 =====
      {
        path: 'carbon',
        name: 'CarbonAnalysis',
        component: () => import('@/views/carbon/CarbonAnalysis.vue'),
        meta: { title: '碳减排分析', keepAlive: true }
      },
      // ===== AI 智能中心 =====
      {
        path: 'ai/predict',
        name: 'AiPredict',
        component: () => import('@/views/ai/AiPredict.vue'),
        meta: { title: 'AI 预测调光', keepAlive: true }
      },
      {
        path: 'ai/assistant',
        name: 'AiAssistant',
        component: () => import('@/views/ai/AiAssistant.vue'),
        meta: { title: 'AI 运维助手', keepAlive: true }
      },
      {
        path: 'ai/knowledge',
        name: 'AiKnowledge',
        component: () => import('@/views/ai/AiKnowledge.vue'),
        meta: { title: '知识库管理', adminOnly: true, keepAlive: true }
      },
      // ===== 系统管理 =====
      {
        path: 'system/users',
        name: 'Users',
        component: () => import('@/views/Users.vue'),
        meta: { title: '用户管理', adminOnly: true, keepAlive: true }
      },
      {
        path: 'system/audit',
        name: 'OperationAudit',
        component: () => import('@/views/system/OperationAudit.vue'),
        meta: { title: '操作审计', adminOnly: true, keepAlive: true }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/SystemConfig.vue'),
        meta: { title: '参数配置', adminOnly: true, keepAlive: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { public: true, title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：登录校验 + 权限校验
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  document.title = to.meta.title
    ? `${to.meta.title} - 智慧路灯管理系统`
    : '智慧路灯管理系统'

  if (to.meta.public) {
    return next()
  }

  if (!userStore.isLogin) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  if (to.meta.adminOnly && !userStore.isAdmin) {
    return next({ path: '/dashboard' })
  }

  next()
})

export default router
