<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import CategoryIcon from '@/components/CategoryIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { categoryApi } from '@/api'
import { CATEGORY_COLORS, CATEGORY_ICON_OPTIONS, resolveCategoryIcon } from '@/utils/categoryIcons'
import { toNumber } from '@/utils/format'

const activeType = ref('EXPENSE')
const loading = ref(false)
const expenseTree = ref([])
const incomeTree = ref([])

const currentTree = computed(() =>
  activeType.value === 'EXPENSE' ? expenseTree.value : incomeTree.value
)

function countNodes(list) {
  return (list || []).reduce((sum, item) => sum + 1 + countNodes(item.children), 0)
}

const counts = computed(() => ({
  EXPENSE: countNodes(expenseTree.value),
  INCOME: countNodes(incomeTree.value)
}))

const parentOptions = computed(() => currentTree.value.filter((item) => item.id !== form.id))

async function loadCategories() {
  loading.value = true
  try {
    const [expense, income] = await Promise.all([
      categoryApi.tree('EXPENSE'),
      categoryApi.tree('INCOME')
    ])
    expenseTree.value = expense || []
    incomeTree.value = income || []
  } catch (error) {
    expenseTree.value = []
    incomeTree.value = []
  } finally {
    loading.value = false
  }
}

/* ================= 新增 / 编辑 ================= */
const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  name: '',
  parentId: null,
  icon: 'other',
  color: '#4F6EF7',
  sort: 0
})

const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 10, message: '分类名称最长 10 个字符', trigger: 'blur' }
  ]
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增分类' : '编辑分类'))

function resetForm() {
  form.id = null
  form.name = ''
  form.parentId = null
  form.icon = 'other'
  form.color = CATEGORY_COLORS[0]
  form.sort = 0
  formRef.value?.clearValidate()
}

function openCreate(parent) {
  dialogMode.value = 'create'
  resetForm()
  if (parent) {
    form.parentId = parent.id
    form.icon = parent.icon
    form.color = parent.color
  }
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name
  form.parentId = row.parentId || null
  form.icon = row.icon || 'other'
  form.color = row.color || CATEGORY_COLORS[0]
  form.sort = toNumber(row.sort)
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      type: activeType.value,
      parentId: form.parentId || null,
      icon: form.icon,
      color: form.color,
      sort: toNumber(form.sort)
    }
    if (dialogMode.value === 'create') {
      await categoryApi.create(payload)
      ElMessage.success('分类已新增')
    } else {
      await categoryApi.update(form.id, payload)
      ElMessage.success('分类已更新')
    }
    dialogVisible.value = false
    loadCategories()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function removeCategory(row) {
  if (row.system) return
  try {
    await ElMessageBox.confirm(
      `删除后该分类下的账单将无法归类，确认删除「${row.name}」吗？`,
      '删除分类',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }
  try {
    await categoryApi.remove(row.id)
    ElMessage.success('分类已删除')
    loadCategories()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  }
}

onMounted(loadCategories)
</script>

<template>
  <div class="page page-stack">
    <section class="card">
      <el-tabs v-model="activeType" class="type-tabs">
        <el-tab-pane :label="`支出分类 (${counts.EXPENSE})`" name="EXPENSE" />
        <el-tab-pane :label="`收入分类 (${counts.INCOME})`" name="INCOME" />
      </el-tabs>

      <div class="category-toolbar">
        <p class="text-sm text-muted">
          支持两级分类，内置分类带有「内置」标记，不可删除；新增账单时会出现在分类选择中。
        </p>
        <el-button type="primary" :icon="Plus" @click="openCreate(null)">新增一级分类</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="currentTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column label="分类名称" min-width="260">
          <template #default="{ row }">
            <span class="cat-cell">
              <CategoryIcon
                :icon="row.icon"
                :name="row.name"
                :color="row.color || '#4F6EF7'"
                :size="30"
              />
              <span class="cat-cell__name">{{ row.name }}</span>
              <el-tag v-if="row.system" size="small" effect="plain" type="info">内置</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="层级" width="100">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.parentId ? '二级分类' : '一级分类' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="账单数" width="110" align="right">
          <template #default="{ row }">
            <span class="num">{{ toNumber(row.billCount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="90" align="right">
          <template #default="{ row }">
            <span class="num text-muted">{{ toNumber(row.sort) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="right">
          <template #default="{ row }">
            <el-button
              v-if="!row.parentId"
              text
              type="primary"
              size="small"
              @click="openCreate(row)"
            >
              添加子分类
            </el-button>
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-tooltip content="内置分类不可删除" placement="top" :disabled="!row.system">
              <span>
                <el-button
                  text
                  type="danger"
                  size="small"
                  :disabled="row.system"
                  @click="removeCategory(row)"
                >
                  删除
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            icon="Collection"
            title="还没有分类"
            description="先创建一级分类，再为它添加常用的二级分类"
          >
            <el-button type="primary" :icon="Plus" @click="openCreate(null)">新增一级分类</el-button>
          </EmptyState>
        </template>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="84px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" maxlength="10" show-word-limit placeholder="例如：咖啡饮品" />
        </el-form-item>
        <el-form-item label="父级分类">
          <el-select v-model="form.parentId" placeholder="不选则为一级分类" clearable style="width: 100%">
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-select v-model="form.icon" filterable style="width: 100%">
            <el-option
              v-for="item in CATEGORY_ICON_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            >
              <span class="icon-option">
                <el-icon v-if="resolveCategoryIcon(item.value)" :size="15">
                  <component :is="resolveCategoryIcon(item.value)" />
                </el-icon>
                <span>{{ item.label }}</span>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="颜色">
          <div class="inline-flex">
            <el-color-picker v-model="form.color" :predefine="CATEGORY_COLORS" />
            <span class="mono text-sm text-muted">{{ form.color }}</span>
          </div>
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sort" :min="0" :max="9999" :step="1" style="width: 160px" />
        </el-form-item>
        <el-form-item label="预览">
          <span class="cat-cell">
            <CategoryIcon :icon="form.icon" :name="form.name" :color="form.color" :size="32" />
            <span class="cat-cell__name">{{ form.name || '未命名分类' }}</span>
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.type-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.type-tabs :deep(.el-tabs__content) {
  display: none;
}

.category-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.icon-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
