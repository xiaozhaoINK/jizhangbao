import axios from 'axios'
import { showToast } from 'vant'

export const TOKEN_KEY = 'jzb_token'
export const USER_KEY = 'jzb_user'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data
      }
      showToast({ message: body.message || '操作失败', position: 'top' })
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (!window.location.pathname.endsWith('/login')) {
        window.location.href = '/login'
      }
      showToast({ message: '登录已过期，请重新登录', position: 'top' })
      return Promise.reject(error)
    }
    const message = error?.response?.data?.message || error.message || '网络异常，请稍后再试'
    showToast({ message, position: 'top' })
    return Promise.reject(error)
  }
)

export default http

/* ===================== 认证 ===================== */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me'),
  updateProfile: (data) => http.put('/users/profile', data),
  changePassword: (data) => http.put('/users/password', data)
}

/* ===================== 分类 ===================== */
export const categoryApi = {
  tree: (type) => http.get('/categories', { params: { type } }),
  flat: (type) => http.get('/categories/flat', { params: { type } }),
  create: (data) => http.post('/categories', data),
  update: (id, data) => http.put(`/categories/${id}`, data),
  remove: (id) => http.delete(`/categories/${id}`)
}

/* ===================== 账单 ===================== */
export const billApi = {
  page: (params) => http.get('/bills', { params }),
  detail: (id) => http.get(`/bills/${id}`),
  create: (data) => http.post('/bills', data),
  update: (id, data) => http.put(`/bills/${id}`, data),
  remove: (id) => http.delete(`/bills/${id}`),
  recent: (limit = 5) => http.get('/bills/recent', { params: { limit } }),
  summary: (params) => http.get('/bills/summary', { params })
}

/* ===================== 统计 ===================== */
export const statsApi = {
  overview: (yearMonth) => http.get('/stats/overview', { params: { yearMonth } }),
  trend: (params) => http.get('/stats/trend', { params }),
  category: (params) => http.get('/stats/category', { params }),
  monthly: (year) => http.get('/stats/monthly', { params: { year } }),
  recentMonths: (months = 6) => http.get('/stats/recent-months', { params: { months } }),
  calendar: (yearMonth) => http.get('/stats/calendar', { params: { yearMonth } })
}

/* ===================== 预算 ===================== */
export const budgetApi = {
  list: (yearMonth) => http.get('/budgets', { params: { yearMonth } }),
  status: (yearMonth) => http.get('/budgets/status', { params: { yearMonth } }),
  create: (data) => http.post('/budgets', data),
  update: (id, data) => http.put(`/budgets/${id}`, data),
  remove: (id) => http.delete(`/budgets/${id}`),
  copyLastMonth: (yearMonth) => http.post('/budgets/copy-last-month', null, { params: { yearMonth } })
}

/* ===================== AI ===================== */
export const aiApi = {
  status: () => http.get('/ai/status'),
  parse: (text) => http.post('/ai/bills/parse', { text }),
  record: (text) => http.post('/ai/bills/record', { text }),
  analysis: (yearMonth) => http.get('/ai/analysis', { params: { yearMonth } }),
  conversations: () => http.get('/ai/conversations'),
  messages: (id) => http.get(`/ai/conversations/${id}/messages`),
  removeConversation: (id) => http.delete(`/ai/conversations/${id}`)
}

/* ===================== 报告 ===================== */
export const reportApi = {
  monthly: (yearMonth) => http.get(`/reports/monthly/${yearMonth}`),
  generate: (yearMonth) => http.post(`/reports/monthly/${yearMonth}/generate`),
  list: (limit = 12) => http.get('/reports/monthly', { params: { limit } })
}

/* ===================== 元数据 ===================== */
export const metaApi = {
  dicts: () => http.get('/meta/dicts')
}

/**
 * AI 聊天使用 SSE，axios 无法逐块读取，这里用 fetch + ReadableStream 手写。
 *
 * @param {Object} payload { conversationId, message }
 * @param {Object} handlers { onMeta, onDelta, onDone, onError }
 */
export async function streamChat(payload, handlers = {}) {
  const token = localStorage.getItem(TOKEN_KEY)
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  })
  if (!response.ok || !response.body) {
    throw new Error('AI 服务连接失败')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (rawEvent) => {
    let event = 'message'
    const dataLines = []
    for (const line of rawEvent.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    if (!dataLines.length) return
    const raw = dataLines.join('\n')
    let data = raw
    try {
      data = JSON.parse(raw)
    } catch (e) {
      // 非 JSON 直接透传字符串
    }
    if (event === 'meta') handlers.onMeta?.(data)
    else if (event === 'delta') handlers.onDelta?.(typeof data === 'string' ? data : data.content || '')
    else if (event === 'done') handlers.onDone?.(data)
    else if (event === 'error') handlers.onError?.(data)
  }

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() || ''
    for (const part of parts) {
      if (part.trim()) dispatch(part)
    }
  }
  if (buffer.trim()) dispatch(buffer)
}
