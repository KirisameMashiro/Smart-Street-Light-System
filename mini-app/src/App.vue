<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

// 需要登录的页面路径
const authPages = [
  'pages/home/index',
  'pages/monitor/index',
  'pages/control/index',
  'pages/detail/index'
]

function isAuthPage(path: string): boolean {
  const pagePath = path.replace(/^\//, '').replace(/\.html$/, '')
  return authPages.some(authPage => pagePath.includes(authPage))
}

function checkLogin(): boolean {
  const user = uni.getStorageSync('user')
  return !!user
}

onLaunch(() => {
  console.log('App Launch')
  
  // 拦截页面跳转，检查登录状态
  uni.addInterceptor('navigateTo', {
    invoke(args) {
      if (isAuthPage(args.url) && !checkLogin()) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        uni.redirectTo({ url: '/pages/login/index' })
        return false
      }
      return true
    }
  })
  
  uni.addInterceptor('switchTab', {
    invoke(args) {
      if (isAuthPage(args.url) && !checkLogin()) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        uni.redirectTo({ url: '/pages/login/index' })
        return false
      }
      return true
    }
  })
  
  uni.addInterceptor('redirectTo', {
    invoke(args) {
      if (isAuthPage(args.url) && !checkLogin()) {
        uni.redirectTo({ url: '/pages/login/index' })
        return false
      }
      return true
    }
  })
})

onShow(() => {
  console.log('App Show')
})

onHide(() => {
  console.log('App Hide')
})
</script>

<style lang="scss">
@import './styles/global.scss';

/* 全局样式覆盖 */
page {
  background-color: #f5f7fa;
}

/* 自定义导航栏样式 */
.uni-navbar {
  background: linear-gradient(135deg, #0a1628 0%, #1a2f4a 100%) !important;
}

/* 滚动条样式优化 */
::-webkit-scrollbar {
  width: 0;
  height: 0;
  background: transparent;
}

/* 按钮点击效果 */
button:active {
  opacity: 0.8;
}

/* 禁用状态 */
.disabled {
  opacity: 0.5;
  pointer-events: none;
}
</style>
