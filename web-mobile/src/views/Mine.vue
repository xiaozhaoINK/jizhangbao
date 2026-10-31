<template>
  <div class="page mine">
    <div class="mine__header">
      <div class="mine__avatar">{{ userStore.avatarText }}</div>
      <div class="mine__info">
        <p class="mine__name">{{ userStore.nickname }}</p>
        <p class="mine__sub">
          已记账 {{ billCount }} 笔 · 连续 {{ overview.continuousDays || 0 }} 天
        </p>
      </div>
      <van-icon name="setting-o" size="20" color="#fff" @click="openProfile" />
    </div>

    <div class="mine__stats card">
      <div class="mine__stat">
        <span class="num text-expense">¥{{ money(overview.monthExpense) }}</span>
        <em>本月支出</em>
      </div>
      <div class="mine__stat">
        <span class="num text-income">¥{{ money(overview.monthIncome) }}</span>
        <em>本月收入</em>
      </div>
      <div class="mine__stat">
        <span class="num">¥{{ money(overview.monthBalance) }}</span>
        <em>本月结余</em>
      </div>
    </div>

    <van-cell-group inset class="mine__group">
      <van-cell title="预算管理" icon="chart-trending-o" is-link to="/budget" />
      <van-cell title="消费洞察报告" icon="notes-o" is-link to="/report" />
      <van-cell title="分类管理" icon="label-o" is-link to="/categories" />
      <van-cell title="AI 助手设置" icon="chat-o" is-link :value="aiStatus.engine" @click="showAi = true" />
    </van-cell-group>

    <van-cell-group inset class="mine__group">
      <van-cell title="账号" :value="userStore.user?.username" />
      <van-cell title="角色" :value="roleText" />
      <van-cell title="版本" value="v1.0.0" />
    </van-cell-group>

    <div class="mine__logout">
      <van-button block round plain type="danger" @click="logout">退出登录</van-button>
    </div>

    <van-popup v-model:show="showProfile" position="bottom" round>
      <div class="mine__popup">
        <p class="mine__popup-title">编辑资料</p>
        <van-field v-model="profile.nickname" label="昵称" placeholder="请输入昵称" maxlength="20" />
        <van-field v-model="profile.email" label="邮箱" placeholder="请输入邮箱" />
        <van-field v-model="profile.phone" label="手机号" placeholder="请输入手机号" maxlength="11" />
        <div class="mine__popup-btn">
          <van-button type="primary" block round :loading="saving" @click="saveProfile">保存</van-button>
        </div>
      </div>
    </van-popup>

    <van-dialog v-model:show="showAi" title="当前 AI 能力" confirm-button-text="知道了">
      <div class="mine__ai">
        <p><strong>引擎：</strong>{{ aiStatus.engine }}</p>
        <p><strong>模型：</strong>{{ aiStatus.model }}</p>
        <p><strong>说明：</strong>{{ aiStatus.description }}</p>
        <p class="mine__ai-tip">
          接入真实大模型：启动后端时加上 <code>--spring.profiles.active=llm</code>，并设置环境变量
          <code>OPENAI_API_KEY</code>。
        </p>
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { aiApi, authApi, billApi, statsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { money } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const overview = ref({})
const billCount = ref(0)
const aiStatus = ref({})
const showProfile = ref(false)
const showAi = ref(false)
const saving = ref(false)
const profile = reactive({ nickname: '', email: '', phone: '' })

const roleText = computed(() => (userStore.user?.role === 'ADMIN' ? '管理员' : '普通用户'))

const load = async () => {
  try {
    const [overviewData, ai] = await Promise.all([statsApi.overview(), aiApi.status()])
    overview.value = overviewData || {}
    aiStatus.value = ai || {}
    const summary = await billApi.summary({})
    billCount.value = summary?.count || 0
  } catch (e) {
    // 拦截器已提示
  }
}

const openProfile = () => {
  profile.nickname = userStore.user?.nickname || ''
  profile.email = userStore.user?.email || ''
  profile.phone = userStore.user?.phone || ''
  showProfile.value = true
}

const saveProfile = async () => {
  saving.value = true
  try {
    const user = await authApi.updateProfile({ ...profile })
    userStore.setSession({ user })
    showToast({ message: '已保存', position: 'top' })
    showProfile.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

const logout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
    await userStore.logout()
    router.replace('/login')
  } catch (e) {
    // 取消
  }
}

onMounted(async () => {
  await load()
})
</script>

<style scoped>
.mine__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 18px 40px;
  background: linear-gradient(150deg, #4f6ef7, #6f5cf6);
  color: #fff;
  border-radius: 0 0 24px 24px;
}

.mine__avatar {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 600;
}

.mine__info {
  flex: 1;
}

.mine__name {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.mine__sub {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.85;
}

.mine__stats {
  display: flex;
  margin-top: -26px;
  position: relative;
  z-index: 2;
}

.mine__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.mine__stat span {
  font-size: 15px;
  font-weight: 600;
}

.mine__stat em {
  font-style: normal;
  font-size: 11px;
  color: #8b95b3;
}

.mine__group {
  margin-top: 12px;
}

.mine__logout {
  margin: 24px 18px 0;
}

.mine__popup {
  padding: 16px 0 24px;
}

.mine__popup-title {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
}

.mine__popup-btn {
  padding: 18px 18px 0;
}

.mine__ai {
  padding: 16px 20px 20px;
  font-size: 13px;
  line-height: 1.9;
  color: #4a5578;
}

.mine__ai p {
  margin: 0;
}

.mine__ai-tip {
  margin-top: 8px !important;
  color: #8b95b3;
  font-size: 12px;
}

.mine__ai code {
  background: #f5f7fb;
  padding: 1px 4px;
  border-radius: 4px;
  font-size: 11px;
}
</style>
