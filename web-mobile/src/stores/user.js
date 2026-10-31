import { defineStore } from 'pinia'
import { authApi, TOKEN_KEY, USER_KEY } from '@/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => Boolean(state.token),
    nickname: (state) => state.user?.nickname || state.user?.username || '未登录',
    avatarText: (state) => (state.user?.nickname || state.user?.username || '账').slice(0, 1)
  },
  actions: {
    setSession({ token, user }) {
      this.token = token || this.token
      this.user = user || this.user
      if (token) localStorage.setItem(TOKEN_KEY, token)
      if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    async login(payload) {
      const data = await authApi.login(payload)
      this.setSession(data)
      return data
    },
    async register(payload) {
      const data = await authApi.register(payload)
      this.setSession(data)
      return data
    },
    async fetchMe() {
      const user = await authApi.me()
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 登出失败也清理本地状态
      }
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
