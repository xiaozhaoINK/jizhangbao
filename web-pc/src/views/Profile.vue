<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Key, User } from '@element-plus/icons-vue'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()

const profileRef = ref(null)
const passwordRef = ref(null)
const profileSaving = ref(false)
const passwordSaving = ref(false)

const profileForm = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatar: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const user = computed(() => userStore.user || {})

const avatarPreview = computed(
  () => profileForm.avatar || user.value.avatar || ''
)

const accountItems = computed(() => [
  { label: '用户名', value: user.value.username || '—' },
  { label: '角色', value: userStore.roleLabel },
  { label: '账号状态', value: user.value.status === 1 ? '正常' : '已禁用' },
  { label: '最后登录', value: formatDateTime(user.value.lastLogin) },
  { label: '注册时间', value: formatDateTime(user.value.createdAt) }
])

const emailValidator = (rule, value, callback) => {
  if (!value) {
    callback()
    return
  }
  if (!/^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(value)) {
    callback(new Error('邮箱格式不正确'))
    return
  }
  callback()
}

const phoneValidator = (rule, value, callback) => {
  if (!value) {
    callback()
    return
  }
  if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('手机号格式不正确'))
    return
  }
  callback()
}

const profileRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 20, message: '昵称最长 20 个字符', trigger: 'blur' }
  ],
  email: [{ validator: emailValidator, trigger: 'blur' }],
  phone: [{ validator: phoneValidator, trigger: 'blur' }]
}

const confirmPasswordValidator = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: confirmPasswordValidator, trigger: 'blur' }]
}

function syncProfile() {
  profileForm.nickname = user.value.nickname || ''
  profileForm.email = user.value.email || ''
  profileForm.phone = user.value.phone || ''
  profileForm.avatar = user.value.avatar || ''
}

async function saveProfile() {
  const valid = await profileRef.value?.validate().catch(() => false)
  if (!valid) return

  profileSaving.value = true
  try {
    await userStore.updateProfile({
      nickname: profileForm.nickname.trim(),
      email: profileForm.email.trim() || null,
      phone: profileForm.phone.trim() || null,
      avatar: profileForm.avatar.trim() || null
    })
    ElMessage.success('个人资料已更新')
    syncProfile()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    profileSaving.value = false
  }
}

async function savePassword() {
  const valid = await passwordRef.value?.validate().catch(() => false)
  if (!valid) return

  passwordSaving.value = true
  try {
    await userApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请使用新密码登录')
    passwordRef.value?.resetFields()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    passwordSaving.value = false
  }
}

onMounted(async () => {
  if (!userStore.user) {
    try {
      await userStore.fetchMe()
    } catch (error) {
      // 401 由响应拦截器处理
    }
  }
  syncProfile()
})
</script>

<template>
  <div class="page page-stack">
    <div class="grid grid-2">
      <section class="card">
        <header class="card__head">
          <div>
            <h2 class="card__title">基本资料</h2>
            <p class="card__subtitle">这些信息会展示在后台的账号区域</p>
          </div>
        </header>

        <div class="profile-summary">
          <span class="profile-summary__avatar">
            <img v-if="avatarPreview" :src="avatarPreview" alt="头像" />
            <template v-else>{{ userStore.avatarText }}</template>
          </span>
          <div class="profile-summary__meta">
            <strong>{{ user.nickname || user.username || '未登录' }}</strong>
            <span class="text-sm text-muted">{{ userStore.roleLabel }}</span>
          </div>
        </div>

        <el-form ref="profileRef" :model="profileForm" :rules="profileRules" label-width="76px">
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" placeholder="选填，用于接收通知" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="profileForm.phone" maxlength="11" placeholder="选填" />
          </el-form-item>
          <el-form-item label="头像地址" prop="avatar">
            <el-input v-model="profileForm.avatar" placeholder="选填，填写图片 URL" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="User" :loading="profileSaving" @click="saveProfile">
              保存资料
            </el-button>
            <el-button @click="syncProfile">重置</el-button>
          </el-form-item>
        </el-form>
      </section>

      <div class="stack-12">
        <section class="card">
          <header class="card__head">
            <div>
              <h2 class="card__title">修改密码</h2>
              <p class="card__subtitle">建议定期更换密码，长度 6-32 位</p>
            </div>
          </header>

          <el-form ref="passwordRef" :model="passwordForm" :rules="passwordRules" label-width="76px">
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                show-password
                autocomplete="current-password"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                show-password
                autocomplete="new-password"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Key" :loading="passwordSaving" @click="savePassword">
                更新密码
              </el-button>
            </el-form-item>
          </el-form>
        </section>

        <section class="card">
          <header class="card__head">
            <div>
              <h2 class="card__title">账号信息</h2>
              <p class="card__subtitle">账号相关的只读信息</p>
            </div>
          </header>

          <el-descriptions :column="1" border size="small">
            <el-descriptions-item
              v-for="item in accountItems"
              :key="item.label"
              :label="item.label"
            >
              <span class="num">{{ item.value }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  margin-bottom: 18px;
  border-radius: var(--jz-radius);
  background: linear-gradient(150deg, #f5f7ff, #fbfcff);
  border: 1px solid #eef1fb;
}

.profile-summary__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--jz-gradient);
  color: #ffffff;
  font-size: 18px;
  font-weight: 600;
  overflow: hidden;
  flex-shrink: 0;
}

.profile-summary__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-summary__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.profile-summary__meta strong {
  font-size: 15px;
  font-weight: 600;
  color: var(--jz-text);
}
</style>
