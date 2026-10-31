<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

const demoAccounts = [
  { label: '普通用户', username: 'demo', password: '123456', desc: '完整体验记账、预算与 AI 洞察' },
  { label: '管理员', username: 'admin', password: '123456', desc: '额外开放平台管理与用户管理' }
]

const highlights = [
  { icon: 'MagicStick', title: '自然语言记账', desc: '一句话自动落账' },
  { icon: 'Wallet', title: '预算超支预警', desc: '实时跟踪分类预算' },
  { icon: 'Document', title: '月度消费报告', desc: '自动生成省钱建议' }
]

function fillAccount(account) {
  form.username = account.username
  form.password = account.password
  errorMessage.value = ''
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  errorMessage.value = ''
  try {
    await userStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功，欢迎回来')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || '/dashboard')
  } catch (error) {
    errorMessage.value = error?.message || '登录失败，请检查账号与密码'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="login__brand">
      <div class="login__glow login__glow--one" />
      <div class="login__glow login__glow--two" />
      <div class="login__grid" />

      <div class="brand-head">
        <span class="brand-head__mark">
          <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
            <path
              d="M4 7.5A2.5 2.5 0 0 1 6.5 5h11A2.5 2.5 0 0 1 20 7.5v9A2.5 2.5 0 0 1 17.5 19h-11A2.5 2.5 0 0 1 4 16.5v-9Z"
              fill="rgba(255,255,255,0.24)"
            />
            <path
              d="M8 10h8M8 14h5"
              stroke="#FFFFFF"
              stroke-width="1.8"
              stroke-linecap="round"
              fill="none"
            />
          </svg>
        </span>
        <span class="brand-head__text">
          <strong>记账宝</strong>
          <small>JIZHANGBAO</small>
        </span>
      </div>

      <div class="brand-copy">
        <p class="brand-copy__eyebrow">智能记账 · 消费洞察</p>
        <h1 class="brand-copy__title">让每一笔支出<br />都有迹可循</h1>
        <p class="brand-copy__desc">
          记录、归类、分析、预警——记账宝把繁琐的账本整理成一份清晰可读的消费结构图。
        </p>
      </div>

      <svg class="brand-visual" viewBox="0 0 520 240" fill="none" aria-hidden="true">
        <defs>
          <linearGradient id="loginLine" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stop-color="#7C5CFF" />
            <stop offset="1" stop-color="#8FA6FF" />
          </linearGradient>
          <linearGradient id="loginArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#8FA6FF" stop-opacity="0.3" />
            <stop offset="1" stop-color="#8FA6FF" stop-opacity="0" />
          </linearGradient>
        </defs>
        <g stroke="rgba(255,255,255,0.08)" stroke-width="1">
          <line x1="0" y1="60" x2="520" y2="60" />
          <line x1="0" y1="120" x2="520" y2="120" />
          <line x1="0" y1="180" x2="520" y2="180" />
        </g>
        <g fill="rgba(255,255,255,0.14)">
          <rect x="46" y="158" width="22" height="52" rx="6" />
          <rect x="96" y="136" width="22" height="74" rx="6" />
          <rect x="146" y="168" width="22" height="42" rx="6" />
          <rect x="196" y="118" width="22" height="92" rx="6" />
          <rect x="246" y="142" width="22" height="68" rx="6" />
          <rect x="296" y="104" width="22" height="106" rx="6" />
          <rect x="346" y="128" width="22" height="82" rx="6" />
          <rect x="396" y="86" width="22" height="124" rx="6" />
        </g>
        <path
          d="M0 196C42 186 72 156 108 160C148 164 168 118 208 124C246 130 268 92 306 104C344 116 366 72 404 80C442 88 486 56 520 62L520 240L0 240Z"
          fill="url(#loginArea)"
        />
        <path
          d="M0 196C42 186 72 156 108 160C148 164 168 118 208 124C246 130 268 92 306 104C344 116 366 72 404 80C442 88 486 56 520 62"
          stroke="url(#loginLine)"
          stroke-width="2.4"
          stroke-linecap="round"
        />
        <circle cx="404" cy="80" r="4.5" fill="#FFFFFF" />
      </svg>

      <ul class="brand-highlights">
        <li v-for="item in highlights" :key="item.title">
          <span class="brand-highlights__icon">
            <el-icon :size="15"><component :is="item.icon" /></el-icon>
          </span>
          <span class="brand-highlights__text">
            <strong>{{ item.title }}</strong>
            <small>{{ item.desc }}</small>
          </span>
        </li>
      </ul>
    </section>

    <section class="login__panel">
      <div class="login-card">
        <header class="login-card__head">
          <h2>登录后台</h2>
          <p>使用记账宝账号继续管理你的账簿</p>
        </header>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              autocomplete="username"
              clearable
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入登录密码"
              :prefix-icon="Lock"
              autocomplete="current-password"
              show-password
              @keyup.enter="submit"
            />
          </el-form-item>

          <p v-if="errorMessage" class="login-card__error">
            <el-icon :size="14"><WarningFilled /></el-icon>
            {{ errorMessage }}
          </p>

          <el-button
            type="primary"
            class="login-card__submit"
            :loading="submitting"
            @click="submit"
          >
            登录
          </el-button>
        </el-form>

        <div class="demo">
          <p class="demo__title">演示账号 · 一键填充</p>
          <div class="demo__list">
            <button
              v-for="account in demoAccounts"
              :key="account.username"
              type="button"
              class="demo__item"
              @click="fillAccount(account)"
            >
              <span class="demo__item-main">
                <strong>{{ account.label }}</strong>
                <span class="demo__item-account mono">{{ account.username }} / {{ account.password }}</span>
              </span>
              <span class="demo__item-desc">{{ account.desc }}</span>
              <el-icon class="demo__item-arrow" :size="13"><Right /></el-icon>
            </button>
          </div>
        </div>

        <p class="login-card__foot">记账宝 · 让每一笔支出都有迹可循</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(480px, 0.85fr);
  min-height: 100vh;
  background: #ffffff;
}

/* ================= 品牌区 ================= */
.login__brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 34px;
  padding: 56px 64px;
  overflow: hidden;
  background: linear-gradient(150deg, #111c34 0%, #17244a 55%, #1b2a55 100%);
  color: #ffffff;
}

.login__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.login__glow--one {
  width: 420px;
  height: 420px;
  top: -120px;
  left: -80px;
  background: rgba(79, 110, 247, 0.55);
}

.login__glow--two {
  width: 360px;
  height: 360px;
  right: -100px;
  bottom: -120px;
  background: rgba(124, 92, 255, 0.42);
}

.login__grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 30% 20%, #000 0%, transparent 72%);
  pointer-events: none;
}

.brand-head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-head__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: var(--jz-gradient);
  box-shadow: 0 10px 26px rgba(79, 110, 247, 0.45);
}

.brand-head__text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-head__text strong {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1.4px;
}

.brand-head__text small {
  margin-top: 4px;
  font-size: 9.5px;
  letter-spacing: 3px;
  color: rgba(255, 255, 255, 0.4);
}

.brand-copy {
  position: relative;
  z-index: 1;
  max-width: 520px;
}

.brand-copy__eyebrow {
  font-size: 12px;
  letter-spacing: 3px;
  color: rgba(255, 255, 255, 0.45);
  text-transform: uppercase;
}

.brand-copy__title {
  margin-top: 14px;
  font-size: 40px;
  line-height: 1.28;
  font-weight: 600;
  letter-spacing: 1.2px;
}

.brand-copy__desc {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.85;
  color: rgba(255, 255, 255, 0.6);
  max-width: 460px;
}

.brand-visual {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 520px;
  height: auto;
}

.brand-highlights {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  list-style: none;
  max-width: 660px;
}

.brand-highlights li {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  border-radius: var(--jz-radius);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.07);
}

.brand-highlights__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  flex-shrink: 0;
  color: #b9c6ff;
  background: rgba(124, 92, 255, 0.24);
}

.brand-highlights__text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.brand-highlights__text strong {
  font-size: 12.5px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.brand-highlights__text small {
  font-size: 11.5px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.45);
}

/* ================= 登录面板 ================= */
.login__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: #ffffff;
}

.login-card {
  width: 100%;
  max-width: 382px;
}

.login-card__head h2 {
  font-size: 24px;
  font-weight: 600;
  color: var(--jz-text);
  letter-spacing: 0.6px;
}

.login-card__head p {
  margin-top: 8px;
  margin-bottom: 26px;
  font-size: 13px;
  color: var(--jz-text-muted);
}

.login-card__error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: -4px 0 14px;
  padding: 9px 12px;
  border-radius: var(--jz-radius-sm);
  background: var(--jz-expense-soft);
  color: #e04545;
  font-size: 12.5px;
}

.login-card__submit {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-size: 15px;
  letter-spacing: 2px;
  border: none;
  background: var(--jz-gradient);
}

.login-card__submit:hover {
  opacity: 0.92;
}

.demo {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px dashed var(--jz-border-strong);
}

.demo__title {
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--jz-text-muted);
  letter-spacing: 0.4px;
}

.demo__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo__item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 32px 10px 14px;
  text-align: left;
  border-radius: var(--jz-radius);
  border: 1px solid var(--jz-border);
  background: #fbfcff;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.demo__item:hover {
  border-color: rgba(79, 110, 247, 0.42);
  background: #f6f8ff;
  transform: translateY(-1px);
}

.demo__item-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.demo__item-main strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--jz-text);
}

.demo__item-account {
  font-size: 12px;
  color: var(--jz-primary);
}

.demo__item-desc {
  font-size: 11.5px;
  color: var(--jz-text-muted);
}

.demo__item-arrow {
  position: absolute;
  top: 50%;
  right: 12px;
  transform: translateY(-50%);
  color: #b6bfd2;
}

.login-card__foot {
  margin-top: 26px;
  text-align: center;
  font-size: 11.5px;
  color: #b0b8c9;
  letter-spacing: 0.6px;
}

@media (max-width: 1024px) {
  .login {
    grid-template-columns: minmax(0, 1fr);
  }

  .login__brand {
    display: none;
  }

  .login__panel {
    min-height: 100vh;
    background: linear-gradient(160deg, #f5f7fb, #ffffff);
  }
}
</style>
