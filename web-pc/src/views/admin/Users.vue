<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import { adminApi } from '@/api'
import { formatDateTime, toNumber } from '@/utils/format'

const loading = ref(false)
const users = ref([])
const total = ref(0)
const keyword = ref('')
const statusLoadingId = ref(null)

const pagination = reactive({ page: 1, size: 10 })

async function loadUsers() {
  loading.value = true
  try {
    const data = await adminApi.users({
      page: pagination.page,
      size: pagination.size,
      keyword: keyword.value.trim() || undefined
    })
    users.value = data?.records || []
    total.value = toNumber(data?.total)
    if (!users.value.length && pagination.page > 1) {
      pagination.page -= 1
      return loadUsers()
    }
  } catch (error) {
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  loadUsers()
}

function resetSearch() {
  keyword.value = ''
  pagination.page = 1
  loadUsers()
}

function handleSizeChange(size) {
  pagination.size = size
  pagination.page = 1
  loadUsers()
}

function handlePageChange(page) {
  pagination.page = page
  loadUsers()
}

async function toggleStatus(row, value) {
  const action = value ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确认${action}用户「${row.username}」吗？${value ? '' : '禁用后该用户将无法登录。'}`,
      `${action}账号`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }

  statusLoadingId.value = row.id
  try {
    await adminApi.updateStatus(row.id, value ? 1 : 0)
    row.status = value ? 1 : 0
    ElMessage.success(`已${action}用户 ${row.username}`)
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    statusLoadingId.value = null
  }
}

onMounted(loadUsers)
</script>

<template>
  <div class="page page-stack">
    <section class="card admin-toolbar">
      <div class="admin-toolbar__search">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名或昵称"
          clearable
          style="width: 240px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="Search" @click="search">搜索</el-button>
        <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
      </div>
      <span class="text-sm text-muted">共 {{ total }} 位用户</span>
    </section>

    <section class="card">
      <el-table v-loading="loading" :data="users" row-key="id">
        <el-table-column label="ID" width="80">
          <template #default="{ row }">
            <span class="num text-muted">#{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户名" min-width="140">
          <template #default="{ row }">
            <span class="text-strong">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="昵称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.nickname || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="row.role === 'ADMIN' ? 'warning' : 'info'">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账单数" width="100" align="right">
          <template #default="{ row }">
            <span class="num">{{ toNumber(row.billCount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="160">
          <template #default="{ row }">
            <span class="text-sm text-secondary num">{{ formatDateTime(row.lastLogin) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            <span class="text-sm text-secondary num">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="130" align="right" fixed="right">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="statusLoadingId === row.id"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(value) => toggleStatus(row, value)"
            />
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            icon="User"
            title="没有匹配的用户"
            description="换个关键字试试，或重置筛选条件"
          >
            <el-button @click="resetSearch">重置筛选</el-button>
          </EmptyState>
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </section>
  </div>
</template>

<style scoped>
.admin-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.admin-toolbar__search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
