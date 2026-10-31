<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import CategoryIcon from '@/components/CategoryIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { budgetApi, categoryApi } from '@/api'
import { currentYearMonth, formatMoney, formatMonthLabel, formatPercent, toNumber } from '@/utils/format'

const yearMonth = ref(currentYearMonth())
const loading = ref(false)
const submitting = ref(false)
const status = ref(null)
const expenseCategories = ref([])

const totalBudget = computed(() => toNumber(status.value?.totalBudget))
const totalUsed = computed(() => toNumber(status.value?.totalUsed))
const totalRemaining = computed(() => toNumber(status.value?.totalRemaining))
const usageRate = computed(() => toNumber(status.value?.usageRate))
const daysLeft = computed(() => toNumber(status.value?.daysLeft))
const dailyAllowance = computed(() => toNumber(status.value?.dailyAllowance))

const alerts = computed(() => status.value?.alerts || [])

const categoryBudgets = computed(() =>
  (status.value?.items || []).filter((item) => toNumber(item.categoryId) > 0)
)

const totalStatus = computed(() => {
  if (!totalBudget.value) return { label: '未设置', type: 'info' }
  if (usageRate.value >= 100) return { label: '已超支', type: 'danger' }
  if (usageRate.value >= 80) return { label: '接近上限', type: 'warning' }
  return { label: '预算健康', type: 'success' }
})

const progressColor = computed(() => {
  if (usageRate.value >= 100) return '#FF5B5B'
  if (usageRate.value >= 80) return '#FF9F0A'
  return '#4F6EF7'
})

const monthLabel = computed(() => formatMonthLabel(yearMonth.value))

const categoryOptions = computed(() =>
  expenseCategories.value.map((item) => ({
    value: item.id,
    label: item.parentId ? `${item.parentName || '子分类'} / ${item.name}` : item.name
  }))
)

function statusMeta(item) {
  if (item.status === 'EXCEEDED') return { label: '已超支', type: 'danger', color: '#FF5B5B' }
  if (item.status === 'WARNING') return { label: '接近上限', type: 'warning', color: '#FF9F0A' }
  return { label: '正常', type: 'success', color: '#00B578' }
}

async function loadStatus() {
  loading.value = true
  try {
    status.value = await budgetApi.status(yearMonth.value)
  } catch (error) {
    status.value = null
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const [flat, tree] = await Promise.all([
      categoryApi.flat('EXPENSE'),
      categoryApi.tree('EXPENSE')
    ])
    const parentMap = new Map((tree || []).map((item) => [item.id, item.name]))
    expenseCategories.value = (flat || []).map((item) => ({
      ...item,
      parentName: item.parentId ? parentMap.get(item.parentId) : ''
    }))
  } catch (error) {
    expenseCategories.value = []
  }
}

/* ================= 新增 / 编辑 ================= */
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)

const form = reactive({
  id: null,
  categoryId: 0,
  amount: null,
  alertThreshold: 80
})

const rules = {
  amount: [
    { required: true, message: '请输入预算金额', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (toNumber(value) <= 1) {
          callback(new Error('预算金额需大于 1 元'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '设置预算' : '编辑预算'))

function resetForm() {
  form.id = null
  form.categoryId = 0
  form.amount = null
  form.alertThreshold = 80
  formRef.value?.clearValidate()
}

function openCreate() {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

function openEdit(item) {
  dialogMode.value = 'edit'
  form.id = item.id
  form.categoryId = toNumber(item.categoryId)
  form.amount = toNumber(item.amount)
  form.alertThreshold = toNumber(item.alertThreshold) || 80
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      yearMonth: yearMonth.value,
      categoryId: form.categoryId,
      amount: Number(toNumber(form.amount).toFixed(2)),
      alertThreshold: toNumber(form.alertThreshold)
    }
    if (dialogMode.value === 'create') {
      await budgetApi.create(payload)
      ElMessage.success('预算已设置')
    } else {
      await budgetApi.update(form.id, payload)
      ElMessage.success('预算已更新')
    }
    dialogVisible.value = false
    loadStatus()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function removeBudget(item) {
  const name = toNumber(item.categoryId) === 0 ? '总预算' : item.categoryName
  try {
    await ElMessageBox.confirm(`确认删除「${name}」的预算吗？`, '删除预算', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (error) {
    return
  }
  try {
    await budgetApi.remove(item.id)
    ElMessage.success('预算已删除')
    loadStatus()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  }
}

async function copyLastMonth() {
  try {
    const data = await budgetApi.copyLastMonth(yearMonth.value)
    ElMessage.success(`已复用上月预算，共 ${toNumber(data?.copied)} 条`)
    loadStatus()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  }
}

onMounted(() => {
  loadCategories()
  loadStatus()
})
</script>

<template>
  <div class="page page-stack">
    <section class="card budget-toolbar">
      <div class="budget-toolbar__month">
        <span class="text-sm text-muted">预算月份</span>
        <el-date-picker
          v-model="yearMonth"
          type="month"
          value-format="YYYY-MM"
          :clearable="false"
          style="width: 150px"
          @change="loadStatus"
        />
        <span class="text-sm text-muted">{{ monthLabel }}</span>
      </div>
      <div class="budget-toolbar__actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadStatus">刷新</el-button>
        <el-button @click="copyLastMonth">复用上月预算</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增预算</el-button>
      </div>
    </section>

    <div v-if="alerts.length" class="alert-banner">
      <el-alert
        v-for="(alert, index) in alerts"
        :key="index"
        :type="alert.level === 'EXCEEDED' ? 'error' : 'warning'"
        :title="alert.title"
        show-icon
        :closable="false"
      >
        {{ alert.message }}
      </el-alert>
    </div>

    <section v-loading="loading" class="card total-card">
      <header class="card__head">
        <div>
          <h2 class="card__title">总预算执行</h2>
          <p class="card__subtitle">{{ monthLabel }} · 全部支出预算进度</p>
        </div>
        <el-tag :type="totalStatus.type" effect="light">{{ totalStatus.label }}</el-tag>
      </header>

      <div class="total-card__body">
        <div class="total-card__main">
          <div class="total-card__numbers">
            <div class="total-card__number">
              <span class="text-sm text-muted">总预算</span>
              <strong class="amount">{{ totalBudget ? formatMoney(totalBudget) : '未设置' }}</strong>
            </div>
            <div class="total-card__number">
              <span class="text-sm text-muted">已使用</span>
              <strong class="amount text-expense">{{ formatMoney(totalUsed) }}</strong>
            </div>
            <div class="total-card__number">
              <span class="text-sm text-muted">剩余</span>
              <strong class="amount" :class="totalRemaining < 0 ? 'text-expense' : 'text-income'">
                {{ formatMoney(totalRemaining) }}
              </strong>
            </div>
          </div>

          <el-progress
            :percentage="Math.min(100, usageRate)"
            :color="progressColor"
            :stroke-width="10"
            :show-text="false"
            class="total-card__progress"
          />
          <p class="total-card__hint">
            使用率 {{ formatPercent(usageRate) }}
            <template v-if="totalBudget">
              · 剩余 {{ daysLeft }} 天 · 日均可用 {{ formatMoney(dailyAllowance) }}
            </template>
            <template v-else>· 建议先设置一个总预算，便于系统提醒</template>
          </p>
        </div>

        <div class="total-card__side">
          <div class="total-card__gauge">
            <span class="total-card__gauge-value">{{ formatPercent(usageRate, 0) }}</span>
            <span class="total-card__gauge-label">已使用比例</span>
          </div>
          <div class="total-card__side-meta">
            <span>剩余天数 <b class="num">{{ daysLeft }}</b> 天</span>
            <span>日均可用 <b class="num">{{ formatMoney(dailyAllowance) }}</b></span>
          </div>
        </div>
      </div>
    </section>

    <section class="card">
      <header class="card__head">
        <div>
          <h2 class="card__title">分类预算</h2>
          <p class="card__subtitle">为高频支出分类单独设定额度，超出阈值会提前提醒</p>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增预算</el-button>
      </header>

      <div v-loading="loading" class="budget-grid">
        <article v-for="item in categoryBudgets" :key="item.id" class="budget-card">
          <header class="budget-card__head">
            <CategoryIcon
              :icon="item.categoryIcon"
              :name="item.categoryName"
              :color="item.categoryColor || '#4F6EF7'"
              :size="32"
            />
            <div class="budget-card__title">
              <strong>{{ item.categoryName }}</strong>
              <span class="text-xs text-muted">提醒阈值 {{ item.alertThreshold }}%</span>
            </div>
            <el-tag :type="statusMeta(item).type" size="small" effect="light">
              {{ statusMeta(item).label }}
            </el-tag>
          </header>

          <div class="budget-card__amount">
            <strong class="amount">{{ formatMoney(item.used) }}</strong>
            <span class="text-muted num">/ {{ formatMoney(item.amount) }}</span>
          </div>

          <el-progress
            :percentage="Math.min(100, toNumber(item.usageRate))"
            :color="statusMeta(item).color"
            :stroke-width="6"
            :show-text="false"
          />

          <footer class="budget-card__foot">
            <span>剩余 {{ formatMoney(item.remaining) }}</span>
            <span>使用率 {{ formatPercent(item.usageRate) }}</span>
            <div class="budget-card__actions">
              <el-button text type="primary" size="small" @click="openEdit(item)">编辑</el-button>
              <el-button text type="danger" size="small" @click="removeBudget(item)">删除</el-button>
            </div>
          </footer>
        </article>

        <EmptyState
          v-if="!loading && !categoryBudgets.length"
          class="budget-grid__empty"
          icon="Wallet"
          title="还没有分类预算"
          description="为餐饮、交通等高频分类设置额度，让花销更有节制"
        >
          <div class="inline-flex">
            <el-button @click="copyLastMonth">复用上月预算</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增预算</el-button>
          </div>
        </EmptyState>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="460px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="预算月份">
          <span class="num">{{ monthLabel }}</span>
        </el-form-item>
        <el-form-item label="预算对象">
          <el-select
            v-model="form.categoryId"
            :disabled="dialogMode === 'edit'"
            style="width: 100%"
            placeholder="选择预算对象"
          >
            <el-option label="总预算（全部支出）" :value="0" />
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预算金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="1"
            :step="100"
            :precision="2"
            :controls-position="'right'"
            placeholder="0.00"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="提醒阈值">
          <el-slider v-model="form.alertThreshold" :min="10" :max="100" :step="5" show-input />
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
.budget-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.budget-toolbar__month {
  display: flex;
  align-items: center;
  gap: 10px;
}

.budget-toolbar__actions {
  display: flex;
  gap: 8px;
}

.total-card__body {
  display: flex;
  align-items: stretch;
  gap: 28px;
}

.total-card__main {
  flex: 1;
  min-width: 0;
}

.total-card__numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.total-card__number {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.total-card__number strong {
  font-size: 22px;
  font-weight: 600;
  color: var(--jz-text);
}

.total-card__progress {
  margin: 18px 0 10px;
}

.total-card__hint {
  font-size: 12.5px;
  color: var(--jz-text-muted);
}

.total-card__side {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px 24px;
  min-width: 210px;
  border-radius: var(--jz-radius-lg);
  background: linear-gradient(160deg, #f4f6ff, #fbfcff);
  border: 1px solid #eef1fb;
}

.total-card__gauge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.total-card__gauge-value {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: var(--jz-primary);
  font-variant-numeric: tabular-nums;
}

.total-card__gauge-label {
  font-size: 12px;
  color: var(--jz-text-muted);
}

.total-card__side-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--jz-text-secondary);
}

.total-card__side-meta b {
  color: var(--jz-text);
}

.budget-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  min-height: 80px;
}

.budget-grid__empty {
  grid-column: 1 / -1;
}

.budget-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: var(--jz-radius);
  border: 1px solid var(--jz-border);
  background: #fcfdff;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.budget-card:hover {
  border-color: #dfe5f5;
  box-shadow: 0 8px 20px rgba(31, 45, 90, 0.06);
}

.budget-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.budget-card__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.budget-card__title strong {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--jz-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.budget-card__amount {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.budget-card__amount strong {
  font-size: 20px;
  color: var(--jz-text);
}

.budget-card__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--jz-text-muted);
}

.budget-card__actions {
  margin-left: auto;
  display: flex;
  gap: 2px;
}

@media (max-width: 1280px) {
  .budget-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .total-card__body {
    flex-direction: column;
  }
}
</style>
