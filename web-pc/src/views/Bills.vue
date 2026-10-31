<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import CategoryIcon from '@/components/CategoryIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { billApi, categoryApi } from '@/api'
import { PAY_METHODS } from '@/config/navigation'
import {
  currentYearMonth,
  formatDate,
  formatMoney,
  monthEnd,
  monthStart,
  todayString,
  toNumber
} from '@/utils/format'

const loading = ref(false)
const submitting = ref(false)
const bills = ref([])
const total = ref(0)
const summary = ref(null)
const expenseTree = ref([])
const incomeTree = ref([])

const defaultRange = () => [monthStart(currentYearMonth()), monthEnd(currentYearMonth())]

const filters = reactive({
  dateRange: defaultRange(),
  type: '',
  categoryId: null,
  keyword: '',
  minAmount: null,
  maxAmount: null
})

const pagination = reactive({ page: 1, size: 10 })

const cascaderProps = {
  value: 'id',
  label: 'name',
  children: 'children',
  emitPath: false,
  checkStrictly: true,
  expandTrigger: 'hover'
}

const filterCategoryOptions = computed(() => {
  if (filters.type === 'EXPENSE') return expenseTree.value
  if (filters.type === 'INCOME') return incomeTree.value
  return [...expenseTree.value, ...incomeTree.value]
})

const queryParams = computed(() => {
  const params = {
    page: pagination.page,
    size: pagination.size,
    sortBy: 'billDate',
    sortOrder: 'desc'
  }
  const [start, end] = filters.dateRange || []
  if (start) params.startDate = start
  if (end) params.endDate = end
  if (filters.type) params.type = filters.type
  if (filters.categoryId) params.categoryId = filters.categoryId
  if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
  if (filters.minAmount !== null && filters.minAmount !== '') params.minAmount = filters.minAmount
  if (filters.maxAmount !== null && filters.maxAmount !== '') params.maxAmount = filters.maxAmount
  return params
})

const rangeLabel = computed(() => {
  const [start, end] = filters.dateRange || []
  if (!start || !end) return '全部时间'
  return `${start} 至 ${end}`
})

const formCategoryOptions = computed(() =>
  form.type === 'EXPENSE' ? expenseTree.value : incomeTree.value
)

const summaryCards = computed(() => [
  { label: '收入合计', value: formatMoney(summary.value?.income), tone: 'income' },
  { label: '支出合计', value: formatMoney(summary.value?.expense), tone: 'expense' },
  { label: '结余', value: formatMoney(summary.value?.balance), tone: 'primary' },
  { label: '账单笔数', value: `${toNumber(summary.value?.count)} 笔`, tone: 'muted' }
])

async function fetchBills() {
  loading.value = true
  try {
    const data = await billApi.page(queryParams.value)
    bills.value = data?.records || []
    total.value = toNumber(data?.total)
    if (!bills.value.length && pagination.page > 1) {
      pagination.page -= 1
      return fetchBills()
    }
  } catch (error) {
    bills.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  const [start, end] = filters.dateRange || []
  try {
    summary.value = await billApi.summary({ startDate: start, endDate: end })
  } catch (error) {
    summary.value = null
  }
}

async function fetchCategories() {
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
  }
}

function reload() {
  fetchBills()
  fetchSummary()
}

function search() {
  pagination.page = 1
  fetchBills()
}

function resetFilters() {
  filters.dateRange = defaultRange()
  filters.type = ''
  filters.categoryId = null
  filters.keyword = ''
  filters.minAmount = null
  filters.maxAmount = null
  pagination.page = 1
  reload()
}

function handleTypeChange() {
  filters.categoryId = null
}

function handleSizeChange(size) {
  pagination.size = size
  pagination.page = 1
  fetchBills()
}

function handlePageChange(page) {
  pagination.page = page
  fetchBills()
}

/* ================= 新增 / 编辑 ================= */
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)

const form = reactive({
  id: null,
  type: 'EXPENSE',
  categoryId: null,
  amount: null,
  billDate: todayString(),
  payMethod: '微信',
  remark: '',
  tags: ''
})

const rules = {
  type: [{ required: true, message: '请选择账单类型', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        const amount = toNumber(value)
        if (amount <= 0) {
          callback(new Error('金额必须大于 0'))
          return
        }
        if (amount > 99999999.99) {
          callback(new Error('金额超出上限'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  billDate: [{ required: true, message: '请选择账单日期', trigger: 'change' }]
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增账单' : '编辑账单'))

function resetForm() {
  form.id = null
  form.type = 'EXPENSE'
  form.categoryId = null
  form.amount = null
  form.billDate = todayString()
  form.payMethod = '微信'
  form.remark = ''
  form.tags = ''
  formRef.value?.clearValidate()
}

function openCreate() {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  form.id = row.id
  form.type = row.type
  form.categoryId = row.categoryId
  form.amount = toNumber(row.amount)
  form.billDate = formatDate(row.billDate, todayString())
  form.payMethod = row.payMethod || ''
  form.remark = row.remark || ''
  form.tags = row.tags || ''
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleFormTypeChange() {
  form.categoryId = null
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      type: form.type,
      categoryId: form.categoryId,
      amount: Number(toNumber(form.amount).toFixed(2)),
      billDate: form.billDate,
      payMethod: form.payMethod || null,
      remark: form.remark || null,
      tags: form.tags || null
    }
    if (dialogMode.value === 'create') {
      await billApi.create(payload)
      ElMessage.success('账单已新增')
    } else {
      await billApi.update(form.id, payload)
      ElMessage.success('账单已更新')
    }
    dialogVisible.value = false
    reload()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function removeBill(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.categoryName}」${formatMoney(row.amount)} 这笔记录吗？`,
      '删除账单',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }
  try {
    await billApi.remove(row.id)
    ElMessage.success('账单已删除')
    reload()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  }
}

onMounted(() => {
  fetchCategories()
  reload()
})
</script>

<template>
  <div class="page page-stack">
    <div class="bills-toolbar">
      <span class="text-sm text-muted">
        {{ rangeLabel }} · 共 {{ total }} 笔账单
      </span>
      <div class="inline-flex">
        <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">记一笔</el-button>
      </div>
    </div>

    <section class="card">
      <div class="filter-bar">
        <div class="filter-item">
          <span class="filter-item__label">日期范围</span>
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
            style="width: 232px"
            @change="search"
          />
        </div>
        <div class="filter-item">
          <span class="filter-item__label">类型</span>
          <el-select
            v-model="filters.type"
            placeholder="全部类型"
            clearable
            style="width: 108px"
            @change="handleTypeChange"
          >
            <el-option label="支出" value="EXPENSE" />
            <el-option label="收入" value="INCOME" />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-item__label">分类</span>
          <el-cascader
            v-model="filters.categoryId"
            :options="filterCategoryOptions"
            :props="cascaderProps"
            placeholder="全部分类"
            clearable
            style="width: 152px"
            @change="search"
          />
        </div>
        <div class="filter-item">
          <span class="filter-item__label">关键字</span>
          <el-input
            v-model="filters.keyword"
            placeholder="备注 / 标签 / AI 原文"
            clearable
            style="width: 168px"
            @keyup.enter="search"
            @clear="search"
          />
        </div>
        <div class="filter-item">
          <span class="filter-item__label">金额区间</span>
          <div class="inline-flex">
            <el-input-number
              v-model="filters.minAmount"
              :min="0"
              :controls="false"
              placeholder="最小"
              style="width: 90px"
            />
            <span class="text-muted">—</span>
            <el-input-number
              v-model="filters.maxAmount"
              :min="0"
              :controls="false"
              placeholder="最大"
              style="width: 90px"
            />
          </div>
        </div>
        <div class="filter-actions">
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="search">查询</el-button>
        </div>
      </div>
    </section>

    <div class="summary-strip">
      <div v-for="item in summaryCards" :key="item.label" class="summary-strip__item">
        <span class="summary-strip__label">{{ item.label }}</span>
        <strong class="summary-strip__value num" :class="`text-${item.tone}`">{{ item.value }}</strong>
      </div>
      <span class="summary-strip__range">{{ rangeLabel }}</span>
    </div>

    <section class="card">
      <el-table v-loading="loading" :data="bills" row-key="id">
        <el-table-column label="日期" width="122">
          <template #default="{ row }">
            <span class="num">{{ formatDate(row.billDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="86">
          <template #default="{ row }">
            <el-tag
              size="small"
              effect="light"
              :type="row.type === 'INCOME' ? 'success' : 'danger'"
            >
              {{ row.type === 'INCOME' ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="190">
          <template #default="{ row }">
            <span class="cat-cell">
              <CategoryIcon
                :icon="row.categoryIcon"
                :name="row.categoryName"
                :color="row.categoryColor || '#4F6EF7'"
                :size="26"
              />
              <span class="cat-cell__name">
                {{ row.categoryName }}
                <span v-if="row.parentCategoryName" class="text-xs text-muted">
                  / {{ row.parentCategoryName }}
                </span>
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">
            <span
              class="amount num"
              :class="row.type === 'INCOME' ? 'text-income' : 'text-expense'"
            >
              {{ row.type === 'INCOME' ? '+' : '-' }}{{ formatMoney(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="row.remark ? '' : 'text-muted'">{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="108">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.payMethod || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="96">
          <template #default="{ row }">
            <el-tooltip
              :content="row.source === 'AI' ? row.aiInput || 'AI 自动记账' : '手动记账'"
              placement="top"
              :disabled="row.source !== 'AI'"
            >
              <el-tag size="small" effect="light" :type="row.source === 'AI' ? 'primary' : 'info'">
                {{ row.source === 'AI' ? 'AI 记账' : '手动' }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="128" align="right" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="removeBill(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            icon="Tickets"
            title="没有符合条件的账单"
            description="试试调整日期范围或筛选条件，也可以立即记一笔"
          >
            <el-button type="primary" :icon="Plus" @click="openCreate">记一笔</el-button>
          </EmptyState>
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="账单类型" prop="type">
          <el-radio-group v-model="form.type" @change="handleFormTypeChange">
            <el-radio-button value="EXPENSE">支出</el-radio-button>
            <el-radio-button value="INCOME">收入</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-cascader
            v-model="form.categoryId"
            :options="formCategoryOptions"
            :props="cascaderProps"
            placeholder="请选择分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :max="99999999.99"
            :precision="2"
            :step="10"
            :controls-position="'right'"
            placeholder="0.00"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="日期" prop="billDate">
          <el-date-picker
            v-model="form.billDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="支付方式" prop="payMethod">
          <el-select v-model="form.payMethod" placeholder="请选择支付方式" clearable style="width: 100%">
            <el-option v-for="method in PAY_METHODS" :key="method" :label="method" :value="method" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="记录一点细节，例如「和朋友聚餐」"
          />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="form.tags" maxlength="200" placeholder="多个标签用逗号分隔" />
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
.bills-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 32px;
}

.summary-strip {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 14px 20px;
  background: #ffffff;
  border: 1px solid var(--jz-border);
  border-radius: var(--jz-radius-lg);
  box-shadow: var(--jz-shadow);
}

.summary-strip__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-strip__label {
  font-size: 12px;
  color: var(--jz-text-muted);
}

.summary-strip__value {
  font-size: 16px;
  font-weight: 600;
}

.summary-strip__range {
  margin-left: auto;
  font-size: 12px;
  color: var(--jz-text-muted);
}
</style>
