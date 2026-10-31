import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import MainLayout from '@/layouts/MainLayout.vue'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据概览', subtitle: '本月收支、预算执行与消费结构一览' }
      },
      {
        path: 'bills',
        name: 'bills',
        component: () => import('@/views/Bills.vue'),
        meta: { title: '账单管理', subtitle: '查询、新增与维护每一笔收支明细' }
      },
      {
        path: 'categories',
        name: 'categories',
        component: () => import('@/views/Categories.vue'),
        meta: { title: '分类管理', subtitle: '维护支出与收入的两级分类体系' }
      },
      {
        path: 'budgets',
        name: 'budgets',
        component: () => import('@/views/Budgets.vue'),
        meta: { title: '预算管理', subtitle: '设置月度预算并跟踪执行进度' }
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('@/views/Stats.vue'),
        meta: { title: '统计分析', subtitle: '趋势、结构与日历热力图多维分析' }
      },
      {
        path: 'ai',
        name: 'ai',
        component: () => import('@/views/Ai.vue'),
        meta: { title: 'AI 助手', subtitle: '自然语言记账与本月消费洞察' }
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/views/Reports.vue'),
        meta: { title: '消费报告', subtitle: '按月生成的消费分析与建议' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心', subtitle: '维护个人资料与登录密码' }
      },
      {
        path: 'admin/overview',
        name: 'admin-overview',
        component: () => import('@/views/admin/Overview.vue'),
        meta: { title: '平台概览', subtitle: '全站用户、账单与 AI 调用情况', requiresAdmin: true }
      },
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', subtitle: '查看用户并启用或禁用账号', requiresAdmin: true }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    if (to.name === 'login' && userStore.token) {
      return { name: 'dashboard' }
    }
    return true
  }

  if (!userStore.token) {
    return {
      name: 'login',
      query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : undefined
    }
  }

  if (!userStore.user) {
    try {
      await userStore.fetchMe()
    } catch (error) {
      userStore.clear()
      return { name: 'login' }
    }
  }

  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    ElMessage.warning('该页面仅管理员可访问')
    return { name: 'dashboard' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 记账宝` : '记账宝 · 后台管理'
})

export default router
