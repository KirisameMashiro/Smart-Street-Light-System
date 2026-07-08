<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">账号管理</h2>
    </div>

    <div class="account-card">
      <div class="card-section">
        <h3 class="section-title">基本信息</h3>
        <el-form ref="infoFormRef" :model="infoForm" :rules="infoRules" label-width="100px">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="infoForm.username" disabled />
          </el-form-item>
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="infoForm.realName" placeholder="请输入真实姓名" />
          </el-form-item>
          <el-form-item label="角色" prop="role">
            <el-tag :type="USER_ROLE_MAP[infoForm.role]?.type" size="small">
              {{ USER_ROLE_MAP[infoForm.role]?.label }}
            </el-tag>
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="infoForm.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="infoForm.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="创建时间">
            <span class="form-value">{{ formatDateTime(userData.createTime) }}</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="infoSubmitting" @click="onSaveInfo">保存修改</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="card-section">
        <h3 class="section-title">修改密码</h3>
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px">
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="pwdSubmitting" @click="onChangePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'Account' })
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getUserList, updateUser, changePassword } from '@/api/user'
import { USER_ROLE_MAP } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()

const infoSubmitting = ref(false)
const pwdSubmitting = ref(false)
const infoFormRef = ref()
const pwdFormRef = ref()

const userData = reactive({})

const infoForm = reactive({
  id: undefined,
  username: '',
  realName: '',
  role: '',
  phone: '',
  email: ''
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const infoRules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }]
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function loadData() {
  try {
    const res = await getUserList()
    const users = res.data || []
    const current = users.find(u => u.username === userStore.user?.username)
    if (current) {
      Object.assign(userData, current)
      Object.assign(infoForm, {
        id: current.id,
        username: current.username,
        realName: current.realName,
        role: current.role,
        phone: current.phone || '',
        email: current.email || ''
      })
    }
  } catch (e) {
    ElMessage.error('加载用户信息失败')
  }
}

async function onSaveInfo() {
  try {
    await infoFormRef.value.validate()
  } catch (e) {
    return
  }
  infoSubmitting.value = true
  try {
    await updateUser(infoForm)
    ElMessage.success('保存成功')
    if (userStore.user) {
      userStore.user.realName = infoForm.realName
      userStore.user.phone = infoForm.phone
      userStore.user.email = infoForm.email
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    infoSubmitting.value = false
  }
}

async function onChangePassword() {
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  pwdSubmitting.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    userStore.logout()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '密码修改失败')
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.account-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.card-section {
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.card-section:last-child {
  border-bottom: none;
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.form-value {
  color: #303133;
  font-weight: 500;
}

@media (max-width: 768px) {
  .account-card {
    padding: 12px;
  }

  .card-section {
    padding-bottom: 16px;
  }

  .section-title {
    font-size: 15px;
  }
}
</style>