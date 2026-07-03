<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-banner">
        <el-icon class="banner-icon"><Sunny /></el-icon>
        <h1>智慧路灯管理系统</h1>
        <p>Smart Street Light Management System</p>
        <ul class="feature-list">
          <li><el-icon><Check /></el-icon> 路灯设备远程控制</li>
          <li><el-icon><Check /></el-icon> 传感器数据实时监控</li>
          <li><el-icon><Check /></el-icon> 设备故障报警管理</li>
        </ul>
      </div>
      <div class="login-form-area">
        <h2 class="form-title">欢迎登录</h2>
        <p class="form-sub">请输入您的账号密码</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="onSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              clearable
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="submit-btn"
              :loading="loading"
              @click="onSubmit"
            >
              登 录
            </el-button>
          </el-form-item>
          <el-form-item>
            <el-button
              class="submit-btn"
              @click="onDemoLogin('admin')"
            >
              演示模式（管理员）
            </el-button>
          </el-form-item>
          <el-form-item>
            <el-button
              class="submit-btn"
              plain
              @click="onDemoLogin('operator')"
            >
              演示模式（运维）
            </el-button>
          </el-form-item>
        </el-form>

        <div class="hint">
          <span>后端未启动时，点击"演示模式"可直接进入</span>
          <span>默认账号：admin / admin123</span>
          <span>运维账号：operator / 123456</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const res = await login(form)
    if (res.code === 200) {
      userStore.setUser(res.data)
      ElMessage.success(res.message || '登录成功')
      const redirect = route.query.redirect || '/dashboard'
      router.push(redirect)
    }
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

// 演示模式：不调后端接口，直接写入用户信息进入系统
function onDemoLogin(role) {
  const demoUsers = {
    admin: { id: 1, username: 'admin', realName: '系统管理员', role: 'admin', status: 1 },
    operator: { id: 2, username: 'operator', realName: '运维人员', role: 'operator', status: 1 }
  }
  userStore.setUser(demoUsers[role])
  ElMessage.success('已进入演示模式')
  const redirect = route.query.redirect || '/dashboard'
  router.push(redirect)
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
  padding: 20px;
}

.login-card {
  width: 880px;
  max-width: 100%;
  height: 480px;
  display: flex;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-banner {
  flex: 1;
  background: linear-gradient(135deg, #0c2461 0%, #1e3799 100%);
  color: #fff;
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.banner-icon {
  font-size: 56px;
  color: #f5a623;
  margin-bottom: 16px;
}

.login-banner h1 {
  font-size: 26px;
  margin: 0 0 8px;
}

.login-banner p {
  margin: 0 0 32px;
  opacity: 0.7;
  font-size: 13px;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 14px;
  opacity: 0.9;
}

.feature-list .el-icon {
  color: #67c23a;
  font-weight: bold;
}

.login-form-area {
  width: 360px;
  padding: 56px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-title {
  margin: 0 0 6px;
  font-size: 24px;
  color: #303133;
}

.form-sub {
  margin: 0 0 28px;
  color: #909399;
  font-size: 13px;
}

.submit-btn {
  width: 100%;
}

.hint {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #c0c4cc;
}

@media (max-width: 720px) {
  .login-wrap {
    padding: 12px;
    align-items: flex-start;
    padding-top: 40px;
  }

  .login-card {
    width: 100%;
    height: auto;
    flex-direction: column;
    border-radius: 8px;
  }

  .login-banner,
  .login-form-area {
    width: 100%;
    padding: 32px 24px;
  }

  .login-banner {
    padding: 24px;
    text-align: center;
  }

  .banner-icon {
    font-size: 40px;
  }

  .login-banner h1 {
    font-size: 20px;
  }

  .feature-list {
    display: none;
  }

  .form-title {
    font-size: 20px;
  }

  .hint {
    font-size: 11px;
  }
}

@media (max-width: 380px) {
  .login-banner {
    padding: 16px;
  }

  .login-form-area {
    padding: 24px 16px;
  }

  .login-banner h1 {
    font-size: 18px;
  }
}
</style>
