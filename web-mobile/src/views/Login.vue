<template>
  <div class="login">
    <div class="login__hero">
      <div class="login__logo">
        <van-icon name="balance-list-o" size="30" color="#fff" />
      </div>
      <h1 class="login__title">记账宝</h1>
      <p class="login__slogan">一句话记账 · 让每一笔支出都有迹可循</p>
      <div class="login__chips">
        <span>AI 自然语言记账</span>
        <span>消费洞察</span>
        <span>预算提醒</span>
      </div>
    </div>

    <div class="login__card">
      <van-tabs v-model:active="mode" line-width="28" animated>
        <van-tab title="登录" name="login" />
        <van-tab title="注册" name="register" />
      </van-tabs>

      <div class="login__form">
        <van-field
          v-model="form.username"
          label="账号"
          placeholder="请输入账号"
          clearable
          left-icon="user-o"
          :maxlength="20"
        />
        <van-field
          v-model="form.password"
          :type="showPassword ? 'text' : 'password'"
          label="密码"
          placeholder="请输入密码"
          left-icon="lock"
          :right-icon="showPassword ? 'eye-o' : 'closed-eye'"
          @click-right-icon="showPassword = !showPassword"
        />
        <van-field
          v-if="mode === 'register'"
          v-model="form.nickname"
          label="昵称"
          placeholder="给自己起个名字（可留空）"
          left-icon="smile-o"
          :maxlength="20"
        />

        <van-button
          type="primary"
          block
          round
          :loading="loading"
          class="login__submit"
          @click="submit"
        >
          {{ mode === 'login' ? '登 录' : '注册并登录' }}
        </van-button>

        <div class="login__hint">
          <span>演示账号</span>
          <van-tag
            v-for="acc in demoAccounts"
            :key="acc.username"
            plain
            type="primary"
            size="medium"
            @click="fillDemo(acc)"
          >
            {{ acc.username }} / {{ acc.password }}
          </van-tag>
        </div>
      </div>
    </div>

    <p class="login__footer">记账宝 · 智能记账 · v1.0.0</p>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const mode = ref('login')
const loading = ref(false)
const showPassword = ref(false)
const form = reactive({ username: '', password: '', nickname: '' })

const demoAccounts = [
  { username: 'demo', password: '123456' },
  { username: 'admin', password: '123456' }
]

const fillDemo = (account) => {
  mode.value = 'login'
  form.username = account.username
  form.password = account.password
}

const submit = async () => {
  if (!form.username.trim()) {
    showToast('请输入账号')
    return
  }
  if (!form.password.trim()) {
    showToast('请输入密码')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      await userStore.login({ username: form.username.trim(), password: form.password })
    } else {
      await userStore.register({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim() || undefined
      })
    }
    showToast({ message: mode.value === 'login' ? '登录成功' : '注册成功', position: 'top' })
    router.replace(route.query.redirect || '/')
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  background: linear-gradient(180deg, #4f6ef7 0%, #6b5cf6 38%, #f5f7fb 38%, #f5f7fb 100%);
  display: flex;
  flex-direction: column;
}

.login__hero {
  padding: 54px 24px 28px;
  color: #fff;
  text-align: center;
}

.login__logo {
  width: 60px;
  height: 60px;
  margin: 0 auto 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(6px);
}

.login__title {
  font-size: 26px;
  margin: 0 0 8px;
  letter-spacing: 2px;
}

.login__slogan {
  margin: 0 0 16px;
  font-size: 13px;
  opacity: 0.9;
}

.login__chips {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.login__chips span {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
}

.login__card {
  margin: 0 16px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(31, 45, 90, 0.12);
  overflow: hidden;
  padding-bottom: 8px;
}

.login__form {
  padding: 8px 4px 16px;
}

.login__submit {
  margin: 18px 16px 0;
  width: calc(100% - 32px);
  height: 44px;
  font-size: 16px;
  background: linear-gradient(135deg, #4f6ef7, #7c5cff);
  border: none;
}

.login__hint {
  margin: 16px 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #8b95b3;
  flex-wrap: wrap;
}

.login__footer {
  margin-top: auto;
  padding: 24px 0 20px;
  text-align: center;
  font-size: 11px;
  color: #b4bcd4;
}
</style>
