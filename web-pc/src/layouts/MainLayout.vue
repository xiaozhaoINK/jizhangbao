<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, SwitchButton, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { NAV_GROUPS } from '@/config/navigation'
import { formatDate } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const navGroups = computed(() =>
  NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.admin || userStore.isAdmin)
  })).filter((group) => group.items.length > 0)
)

const activeGroup = computed(() => {
  const group = NAV_GROUPS.find((item) => item.items.some((link) => link.name === route.name))
  return group?.title || '概览'
})

const pageTitle = computed(() => route.meta.title || '数据概览')
const pageSubtitle = computed(() => route.meta.subtitle || '')
const today = formatDate(new Date())

async function handleCommand(command) {
  if (command === 'profile') {
    router.push({ name: 'profile' })
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('退出后需要重新登录才能继续管理账本，确认退出吗？', '退出登录', {
        confirmButtonText: '退出登录',
        cancelButtonText: '再想想',
        type: 'warning'
      })
    } catch (error) {
      return
    }
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.replace({ name: 'login' })
  }
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <router-link :to="{ name: 'dashboard' }" class="brand">
        <span class="brand__mark">
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <path
              d="M4 7.5A2.5 2.5 0 0 1 6.5 5h11A2.5 2.5 0 0 1 20 7.5v9A2.5 2.5 0 0 1 17.5 19h-11A2.5 2.5 0 0 1 4 16.5v-9Z"
              fill="rgba(255,255,255,0.22)"
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
        <span class="brand__text">
          <strong>记账宝</strong>
          <small>JIZHANGBAO</small>
        </span>
      </router-link>

      <nav class="nav">
        <div v-for="group in navGroups" :key="group.title" class="nav-group">
          <p class="nav-group__title">{{ group.title }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.name"
            :to="{ name: item.name }"
            class="nav-item"
            :class="{ 'is-active': route.name === item.name }"
          >
            <el-icon :size="17"><component :is="item.icon" /></el-icon>
            <span class="nav-item__label">{{ item.label }}</span>
          </router-link>
        </div>
      </nav>

      <footer class="sidebar__footer">
        <div class="sidebar__tip">
          <span class="sidebar__dot" />
          <span>数据实时同步</span>
        </div>
        <p class="sidebar__version">记账宝 · v1.0.0</p>
      </footer>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar__heading">
          <p class="topbar__crumb">
            <span>记账宝</span>
            <i>/</i>
            <span>{{ activeGroup }}</span>
          </p>
          <h1 class="topbar__title">
            {{ pageTitle }}
            <span class="topbar__subtitle">{{ pageSubtitle }}</span>
          </h1>
        </div>

        <div class="topbar__actions">
          <span class="topbar__date">{{ today }}</span>
          <span class="topbar__divider" />
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="account">
              <span class="account__avatar">
                <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" alt="头像" />
                <template v-else>{{ userStore.avatarText }}</template>
              </span>
              <span class="account__meta">
                <strong>{{ userStore.nickname }}</strong>
                <small>{{ userStore.roleLabel }}</small>
              </span>
              <el-icon class="account__caret" :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--jz-bg);
}

/* ================= 侧边栏 ================= */
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--jz-sidebar-width);
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--jz-sidebar-from), var(--jz-sidebar-to));
  color: rgba(255, 255, 255, 0.68);
  z-index: 20;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--jz-header-height);
  padding: 0 18px;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--jz-gradient);
  box-shadow: 0 6px 16px rgba(79, 110, 247, 0.35);
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand__text strong {
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 1px;
}

.brand__text small {
  font-size: 9px;
  letter-spacing: 2.4px;
  color: rgba(255, 255, 255, 0.4);
  margin-top: 3px;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px 8px;
}

.nav::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
}

.nav-group + .nav-group {
  margin-top: 18px;
}

.nav-group__title {
  padding: 0 10px 8px;
  font-size: 11px;
  letter-spacing: 1.2px;
  color: rgba(255, 255, 255, 0.32);
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  margin-bottom: 4px;
  border-radius: var(--jz-radius);
  font-size: 13.5px;
  color: rgba(255, 255, 255, 0.66);
  transition: background 0.18s ease, color 0.18s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #ffffff;
}

.nav-item.is-active {
  color: #ffffff;
  background: linear-gradient(90deg, rgba(79, 110, 247, 0.32), rgba(79, 110, 247, 0.06));
}

.nav-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--jz-primary);
  box-shadow: 0 0 12px rgba(79, 110, 247, 0.8);
}

.nav-item__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar__footer {
  flex-shrink: 0;
  padding: 14px 18px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.sidebar__tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.5);
}

.sidebar__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--jz-income);
  box-shadow: 0 0 8px rgba(0, 181, 120, 0.8);
}

.sidebar__version {
  margin-top: 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.28);
  letter-spacing: 0.4px;
}

/* ================= 主区域 ================= */
.main {
  flex: 1;
  min-width: 0;
  margin-left: var(--jz-sidebar-width);
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  height: var(--jz-header-height);
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--jz-border);
}

.topbar__heading {
  min-width: 0;
}

.topbar__crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--jz-text-muted);
  line-height: 1.4;
}

.topbar__crumb i {
  font-style: normal;
  color: #c9d0df;
}

.topbar__title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 17px;
  font-weight: 600;
  color: var(--jz-text);
  line-height: 1.4;
  letter-spacing: 0.2px;
}

.topbar__subtitle {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--jz-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar__actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.topbar__date {
  font-size: 12.5px;
  color: var(--jz-text-muted);
  font-variant-numeric: tabular-nums;
}

.topbar__divider {
  width: 1px;
  height: 20px;
  background: var(--jz-border-strong);
}

.account {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 6px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.18s ease;
  outline: none;
}

.account:hover {
  background: #f5f7fd;
}

.account__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--jz-gradient);
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  flex-shrink: 0;
}

.account__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account__meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.account__meta strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--jz-text);
}

.account__meta small {
  font-size: 11px;
  color: var(--jz-text-muted);
}

.account__caret {
  color: var(--jz-text-muted);
}

.content {
  flex: 1;
  min-width: 0;
  padding-bottom: 8px;
}

@media (max-width: 960px) {
  .topbar__subtitle,
  .topbar__date {
    display: none;
  }
}
</style>
