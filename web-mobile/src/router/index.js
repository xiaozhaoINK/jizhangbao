import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_KEY } from '@/api'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/TabLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/Home.vue'), meta: { title: '记账宝' } },
      { path: 'bills', name: 'bills', component: () => import('@/views/Bills.vue'), meta: { title: '账单' } },
      { path: 'ai', name: 'ai', component: () => import('@/views/AiChat.vue'), meta: { title: 'AI 助手' } },
      { path: 'stats', name: 'stats', component: () => import('@/views/Stats.vue'), meta: { title: '统计' } },
      { path: 'mine', name: 'mine', component: () => import('@/views/Mine.vue'), meta: { title: '我的' } }
    ]
  },
  {
    path: '/bill/add',
    name: 'bill-add',
    component: () => import('@/views/AddBill.vue'),
    meta: { title: '记一笔' }
  },
  {
    path: '/bill/:id/edit',
    name: 'bill-edit',
    component: () => import('@/views/AddBill.vue'),
    meta: { title: '编辑账单' }
  },
  {
    path: '/budget',
    name: 'budget',
    component: () => import('@/views/Budget.vue'),
    meta: { title: '预算' }
  },
  {
    path: '/report',
    name: 'report',
    component: () => import('@/views/Report.vue'),
    meta: { title: '消费洞察' }
  },
  {
    path: '/categories',
    name: 'categories',
    component: () => import('@/views/Categories.vue'),
    meta: { title: '分类管理' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (to.meta.public) {
    return token && to.name === 'login' ? { name: 'home' } : true
  }
  if (!token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 记账宝` : '记账宝'
})

export default router
