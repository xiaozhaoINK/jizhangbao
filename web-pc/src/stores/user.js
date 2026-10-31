import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, clearAuthStorage, TOKEN_KEY, USER_KEY, userApi } from '@/api'

function readCachedUser() {
  const cached = localStorage.getItem(USER_KEY)
  if (!cached) return null
  try {
    return JSON.parse(cached)
  } catch (error) {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(readCachedUser())
  const loading = ref(false)

  const isLogin = computed(() => Boolean(token.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const nickname = computed(() => user.value?.nickname || user.value?.username || '未登录')
  const roleLabel = computed(() => (isAdmin.value ? '管理员' : '普通用户'))
  const avatarText = computed(() => nickname.value.trim().charAt(0).toUpperCase())

  function setToken(value) {
    token.value = value || ''
    if (token.value) {
      localStorage.setItem(TOKEN_KEY, token.value)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  function setUser(value) {
    user.value = value || null
    if (value) {
      localStorage.setItem(USER_KEY, JSON.stringify(value))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  }

  function clear() {
    clearAuthStorage()
    token.value = ''
    user.value = null
  }

  async function login(form) {
    loading.value = true
    try {
      const data = await authApi.login(form)
      setToken(data?.token)
      setUser(data?.user)
      return data
    } finally {
      loading.value = false
    }
  }

  async function fetchMe() {
    const data = await authApi.me()
    setUser(data)
    return data
  }

  async function updateProfile(payload) {
    const data = await userApi.updateProfile(payload)
    setUser(data)
    return data
  }

  async function logout() {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch (error) {
      // 服务端登出失败时仍然清除本地凭证，保证前端可以退出
    } finally {
      clear()
    }
  }

  return {
    token,
    user,
    loading,
    isLogin,
    isAdmin,
    nickname,
    roleLabel,
    avatarText,
    setUser,
    clear,
    login,
    fetchMe,
    updateProfile,
    logout
  }
})
