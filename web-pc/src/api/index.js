import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'jzb_token'
export const USER_KEY = 'jzb_user'

const HTTP_MESSAGES = {
  400: '请求参数有误',
  403: '没有访问权限',
  404: '请求的资源不存在',
  405: '请求方法不被允许',
  429: '操作过于频繁，请稍后再试',
  500: '服务器开小差了，请稍后重试',
  502: '网关异常，请稍后重试',
  503: '服务暂不可用'
}

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function clearAuthStorage() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

function redirectToLogin() {
  clearAuthStorage()
  if (typeof window === 'undefined') return
  if (window.location.pathname.startsWith('/login')) return
  const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
  window.location.replace(`/login?redirect=${redirect}`)
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (response.config.responseType === 'blob') return payload
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code === 0) return payload.data
      const message = payload.message || '请求失败'
      ElMessage.error(message)
      const error = new Error(message)
      error.code = payload.code
      error.payload = payload
      return Promise.reject(error)
    }
    return payload
  },
  (error) => {
    const status = error.response?.status
    const payload = error.response?.data
    const message = payload?.message || HTTP_MESSAGES[status] || '网络异常，请稍后重试'
    if (status === 401) {
      ElMessage.error('登录状态已失效，请重新登录')
      redirectToLogin()
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

/* ======================= 认证 ======================= */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout')
}

/* ======================= 用户资料 ======================= */
export const userApi = {
  profile: () => http.get('/users/profile'),
  updateProfile: (data) => http.put('/users/profile', data),
  changePassword: (data) => http.put('/users/password', data)
}

/* ======================= 分类 ======================= */
export const categoryApi = {
  tree: (type) => http.get('/categories', { params: { type } }),
  flat: (type) => http.get('/categories/flat', { params: { type } }),
  create: (data) => http.post('/categories', data),
  update: (id, data) => http.put(`/categories/${id}`, data),
  remove: (id) => http.delete(`/categories/${id}`)
}

/* ======================= 账单 ======================= */
export const billApi = {
  page: (params) => http.get('/bills', { params }),
  detail: (id) => http.get(`/bills/${id}`),
  create: (data) => http.post('/bills', data),
  update: (id, data) => http.put(`/bills/${id}`, data),
  remove: (id) => http.delete(`/bills/${id}`),
  recent: (limit = 5) => http.get('/bills/recent', { params: { limit } }),
  summary: (params) => http.get('/bills/summary', { params })
}

/* ======================= 统计 ======================= */
export const statsApi = {
  overview: (yearMonth) => http.get('/stats/overview', { params: { yearMonth } }),
  trend: (params) => http.get('/stats/trend', { params }),
  category: (params) => http.get('/stats/category', { params }),
  monthly: (year) => http.get('/stats/monthly', { params: { year } }),
  recentMonths: (months = 6) => http.get('/stats/recent-months', { params: { months } }),
  calendar: (yearMonth) => http.get('/stats/calendar', { params: { yearMonth } })
}

/* ======================= 预算 ======================= */
export const budgetApi = {
  list: (yearMonth) => http.get('/budgets', { params: { yearMonth } }),
  status: (yearMonth) => http.get('/budgets/status', { params: { yearMonth } }),
  create: (data) => http.post('/budgets', data),
  update: (id, data) => http.put(`/budgets/${id}`, data),
  remove: (id) => http.delete(`/budgets/${id}`),
  copyLastMonth: (yearMonth) =>
    http.post('/budgets/copy-last-month', null, { params: { yearMonth } })
}

/* ======================= AI ======================= */
export const aiApi = {
  status: () => http.get('/ai/status'),
  parse: (text) => http.post('/ai/bills/parse', { text }),
  record: (text) => http.post('/ai/bills/record', { text }),
  analysis: (yearMonth) => http.post('/ai/analysis', null, { params: { yearMonth } }),
  conversations: () => http.get('/ai/conversations'),
  messages: (id) => http.get(`/ai/conversations/${id}/messages`),
  removeConversation: (id) => http.delete(`/ai/conversations/${id}`)
}

/* ======================= 报告 ======================= */
export const reportApi = {
  monthly: (yearMonth) => http.get(`/reports/monthly/${yearMonth}`),
  generate: (yearMonth) => http.post(`/reports/monthly/${yearMonth}/generate`),
  list: (limit = 12) => http.get('/reports/monthly', { params: { limit } })
}

/* ======================= 后台管理 ======================= */
export const adminApi = {
  overview: () => http.get('/admin/overview'),
  users: (params) => http.get('/admin/users', { params }),
  updateStatus: (id, status) =>
    http.put(`/admin/users/${id}/status`, null, { params: { status } }),
  aiUsage: () => http.get('/admin/ai-usage')
}

export default http
