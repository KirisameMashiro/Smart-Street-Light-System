<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-banner">
        <div class="banner-icons">
          <span class="animal-icon">🦁</span>
          <span class="animal-icon">🐘</span>
          <span class="animal-icon">🦒</span>
        </div>
        <h1>动物园智慧照明系统</h1>
        <p>Zoo Smart Lighting System</p>
        <ul class="feature-list">
          <li><el-icon><Check /></el-icon> 园区照明智能控制</li>
          <li><el-icon><Check /></el-icon> 能耗数据实时监控</li>
          <li><el-icon><Check /></el-icon> 设备故障智能预警</li>
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
          </el-form>

        <div class="hint">
          <span>默认账号：admin / 123456</span>
          <span>运维账号：zhangsan / 123456</span>
          <span>市政账号：shizheng / 123456</span>
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


</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1b5e20 0%, #2e7d32 50%, #43a047 100%);
  padding: 20px;
}

.login-card {
  width: 880px;
  max-width: 100%;
  height: 480px;
  display: flex;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
}

.login-banner {
  flex: 1;
  background: linear-gradient(160deg, #1b5e20 0%, #2e7d32 60%, #388e3c 100%);
  color: #fff;
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-banner::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.05'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
  opacity: 0.3;
}

.banner-icons {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.animal-icon {
  font-size: 42px;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.2));
  animation: bounce 2s ease-in-out infinite;
}

.animal-icon:nth-child(2) { animation-delay: 0.2s; }
.animal-icon:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.login-banner h1 {
  font-size: 26px;
  margin: 0 0 8px;
  position: relative;
}

.login-banner p {
  margin: 0 0 32px;
  opacity: 0.8;
  font-size: 13px;
  position: relative;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
  position: relative;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 14px;
  opacity: 0.95;
}

.feature-list .el-icon {
  color: #a5d6a7;
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
  background: linear-gradient(135deg, #2e7d32 0%, #43a047 100%);
  border: none;
}

.submit-btn:hover {
  background: linear-gradient(135deg, #1b5e20 0%, #2e7d32 100%);
}

.hint {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #a8b3a0;
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
    border-radius: 12px;
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

  .banner-icons {
    justify-content: center;
  }

  .animal-icon {
    font-size: 32px;
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
