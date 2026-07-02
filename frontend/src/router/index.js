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
        meta: { title: '控制台', icon: 'Odometer' }
      },
      {
        path: 'lights',
        name: 'Lights',
        component: () => import('@/views/Lights.vue'),
        meta: { title: '路灯管理', icon: 'Sunny' }
      },
      {
        path: 'lights/:id',
        name: 'LightDetail',
        component: () => import('@/views/LightDetail.vue'),
        meta: { title: '路灯详情', hidden: true }
      },
      {
        path: 'sensor-data',
        name: 'SensorData',
        component: () => import('@/views/SensorData.vue'),
        meta: { title: '传感器数据', icon: 'DataLine' }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/views/Alerts.vue'),
        meta: { title: '报警管理', icon: 'Bell' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/Users.vue'),
        meta: { title: '用户管理', icon: 'User', adminOnly: true }
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
