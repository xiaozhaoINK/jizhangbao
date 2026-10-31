<template>
  <div class="page page--plain budget">
    <van-nav-bar title="预算管理" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span class="budget__nav" @click="openCreate(0)">设置</span>
      </template>
    </van-nav-bar>

    <div class="budget__hero">
      <MonthSwitcher v-model="month" />
      <div class="budget__ring">
        <EChart :option="ringOption" height="180px" />
        <div class="budget__ring-center">
          <p class="budget__ring-value num">{{ Math.round(status.usageRate || 0) }}%</p>
          <p class="budget__ring-label">预算使用率</p>
        </div>
      </div>
      <div class="budget__meta">
        <div>
          <span>预算</span>
          <strong class="num">¥{{ money(status.totalBudget) }}</strong>
        </div>
        <div>
          <span>已用</span>
          <strong class="num">¥{{ money(status.totalUsed) }}</strong>
        </div>
        <div>
          <span>{{ status.totalRemaining >= 0 ? '剩余' : '超支' }}</span>
          <strong class="num" :class="status.totalRemaining >= 0 ? '' : 'text-expense'">
            ¥{{ money(Math.abs(status.totalRemaining || 0)) }}
          </strong>
        </div>
      </div>
      <p class="budget__tip">
        剩余 {{ status.daysLeft || 0 }} 天 · 日均可用 ¥{{ money(status.dailyAllowance) }}
      </p>
    </div>

    <div v-if="status.alerts?.length" class="budget__alerts">
      <van-notice-bar
        v-for="alert in status.alerts"
        :key="alert.title"
        left-icon="warning-o"
        :color="alert.level === 'EXCEEDED' ? '#ff5b5b' : '#ff9f0a'"
        :background="alert.level === 'EXCEEDED' ? '#fff1f1' : '#fff8e6'"
        :text="`${alert.title}：${alert.message}`"
        wrapable
        class="budget__alert"
      />
    </div>

    <div class="card">
      <div class="card__title">
        <span>分类预算</span>
        <span class="more" @click="openCreate()">+ 添加</span>
      </div>
      <template v-if="items.length">
        <div v-for="item in items" :key="item.id" class="budget__item" @click="openEdit(item)">
          <div class="budget__item-head">
            <span class="budget__item-name">
              <van-icon name="label-o" :color="item.categoryColor" size="14" />
              {{ item.categoryName }}
            </span>
            <span class="num" :class="statusClass(item.status)">
              ¥{{ money(item.used) }} / ¥{{ money(item.amount) }}
            </span>
          </div>
          <van-progress
            :percentage="Math.min(100, Math.round(item.usageRate || 0))"
            :color="progressColor(item.usageRate)"
            :show-pivot="false"
            track-color="#f2f4fa"
            stroke-width="6"
          />
          <div class="budget__item-foot">
            <span>{{ percent(item.usageRate) }}</span>
            <span>日均可用 ¥{{ money(item.dailyAllowance) }}</span>
          </div>
        </div>
      </template>
      <van-empty v-else description="还没有分类预算" image-size="70" />
    </div>

    <div class="budget__actions">
      <van-button block round plain type="primary" @click="copyLastMonth">复用上月预算</van-button>
    </div>

    <van-popup v-model:show="showForm" position="bottom" round>
      <div class="budget__form">
        <p class="budget__form-title">{{ form.id ? '修改预算' : '设置预算' }}</p>
        <van-field
          v-if="!form.id"
          :model-value="form.categoryName"
          label="预算对象"
          readonly
          is-link
          @click="showCategoryPicker = true"
        />
        <van-field
          v-model="form.amount"
          type="number"
          label="预算金额"
          placeholder="请输入预算金额"
        >
          <template #extra><span class="text-weak">元</span></template>
        </van-field>
        <van-field label="提醒阈值">
          <template #input>
            <van-slider v-model="form.alertThreshold" :min="50" :max="100" :step="10" class="budget__slider" />
            <span class="budget__slider-value num">{{ form.alertThreshold }}%</span>
          </template>
        </van-field>
        <div class="budget__form-btn">
          <van-button
            v-if="form.id"
            round
            plain
            type="danger"
            class="budget__form-del"
            @click="removeBudget"
          >
            删除
          </van-button>
          <van-button type="primary" round :loading="saving" class="budget__form-save" @click="save">
            保存
          </van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <div class="budget__picker">
        <div class="budget__picker-head">
          <span>选择分类</span>
          <van-icon name="cross" @click="showCategoryPicker = false" />
        </div>
        <div class="budget__picker-body">
          <div class="budget__picker-item" @click="pickCategory({ id: 0, name: '总预算' })">总预算（本月全部支出）</div>
          <div
            v-for="item in expenseCategories"
            :key="item.id"
            class="budget__picker-item"
            @click="pickCategory(item)"
          >
            {{ item.name }}
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { budgetApi, categoryApi } from '@/api'
import { currentMonth, money, percent } from '@/utils/format'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import EChart from '@/components/EChart.vue'

const router = useRouter()

const month = ref(currentMonth())
const status = ref({ items: [], alerts: [] })
const items = ref([])
const expenseCategories = ref([])
const showForm = ref(false)
const showCategoryPicker = ref(false)
const saving = ref(false)
const form = reactive({ id: null, categoryId: 0, categoryName: '总预算', amount: '', alertThreshold: 80 })

const ringOption = computed(() => {
  const rate = Math.min(100, Math.max(0, Number(status.value.usageRate || 0)))
  const color = rate >= 100 ? '#ff5b5b' : rate >= 80 ? '#ff9f0a' : '#4f6ef7'
  return {
    series: [
      {
        type: 'gauge',
        startAngle: 90,
        endAngle: -270,
        radius: '92%',
        pointer: { show: false },
        progress: {
          show: true,
          overlap: false,
          roundCap: true,
          clip: false,
          itemStyle: { color }
        },
        axisLine: { lineStyle: { width: 12, color: [[1, '#eef1f9']] } },
        splitLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        detail: { show: false },
        data: [{ value: rate }]
      }
    ]
  }
})

const progressColor = (rate) => {
  const value = Number(rate || 0)
  if (value >= 100) return '#ff5b5b'
  if (value >= 80) return '#ff9f0a'
  return '#4f6ef7'
}

const statusClass = (value) => {
  if (value === 'EXCEEDED') return 'text-expense'
  if (value === 'WARNING') return ''
  return 'text-income'
}

const load = async () => {
  try {
    const data = await budgetApi.status(month.value)
    status.value = data || { items: [], alerts: [] }
    items.value = (data?.items || []).filter((item) => item.categoryId !== 0)
  } catch (e) {
    status.value = { items: [], alerts: [] }
    items.value = []
  }
}

const loadCategories = async () => {
  try {
    expenseCategories.value = (await categoryApi.flat('EXPENSE')) || []
  } catch (e) {
    expenseCategories.value = []
  }
}

const openCreate = (categoryId = 0) => {
  const target = expenseCategories.value.find((item) => item.id === categoryId)
  form.id = null
  form.categoryId = categoryId
  form.categoryName = target?.name || '总预算'
  form.amount = ''
  form.alertThreshold = 80
  showForm.value = true
}

const openEdit = (item) => {
  form.id = item.id
  form.categoryId = item.categoryId
  form.categoryName = item.categoryName
  form.amount = String(item.amount)
  form.alertThreshold = item.alertThreshold || 80
  showForm.value = true
}

const pickCategory = (item) => {
  form.categoryId = item.id
  form.categoryName = item.name
  showCategoryPicker.value = false
}

const save = async () => {
  if (!Number(form.amount)) {
    showToast('请输入预算金额')
    return
  }
  saving.value = true
  try {
    const payload = {
      yearMonth: month.value,
      categoryId: form.categoryId,
      amount: Number(form.amount),
      alertThreshold: form.alertThreshold
    }
    if (form.id) {
      await budgetApi.update(form.id, payload)
    } else {
      await budgetApi.create(payload)
    }
    showToast({ message: '已保存', position: 'top' })
    showForm.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

const removeBudget = async () => {
  try {
    await showConfirmDialog({ title: '删除预算', message: '确定删除这条预算吗？' })
    await budgetApi.remove(form.id)
    showForm.value = false
    showToast({ message: '已删除', position: 'top' })
    await load()
  } catch (e) {
    // 取消
  }
}

const copyLastMonth = async () => {
  try {
    const result = await budgetApi.copyLastMonth(month.value)
    showToast({ message: `已复制 ${result?.copied || 0} 条预算`, position: 'top' })
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

watch(month, load)

onMounted(async () => {
  await Promise.all([load(), loadCategories()])
})
</script>

<style scoped>
.budget__nav {
  font-size: 13px;
  color: #4f6ef7;
}

.budget__hero {
  background: linear-gradient(150deg, #4f6ef7, #6f5cf6);
  color: #fff;
  padding: 4px 16px 20px;
  border-radius: 0 0 24px 24px;
}

.budget__ring {
  position: relative;
  margin: 0 auto;
  width: 200px;
}

.budget__ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.budget__ring-value {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.budget__ring-label {
  margin: 2px 0 0;
  font-size: 11px;
  opacity: 0.85;
}

.budget__meta {
  display: flex;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 14px;
  padding: 12px 0;
  margin-top: 8px;
}

.budget__meta div {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.budget__meta span {
  font-size: 11px;
  opacity: 0.82;
}

.budget__meta strong {
  font-size: 15px;
}

.budget__tip {
  text-align: center;
  font-size: 12px;
  opacity: 0.86;
  margin: 10px 0 0;
}

.budget__alerts {
  padding: 12px 14px 0;
}

.budget__alert {
  border-radius: 10px;
  margin-bottom: 8px;
}

.budget__item {
  padding: 12px 0;
  border-bottom: 1px solid #f4f6fb;
}

.budget__item:last-child {
  border-bottom: none;
}

.budget__item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  margin-bottom: 8px;
}

.budget__item-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.budget__item-foot {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #8b95b3;
  margin-top: 6px;
}

.budget__actions {
  margin: 18px 14px 30px;
}

.budget__form {
  padding: 18px 0 24px;
}

.budget__form-title {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
}

.budget__slider {
  flex: 1;
  margin-right: 12px;
}

.budget__slider-value {
  width: 42px;
  text-align: right;
  font-size: 13px;
}

.budget__form-btn {
  display: flex;
  gap: 12px;
  padding: 20px 18px 0;
}

.budget__form-del {
  flex: 1;
}

.budget__form-save {
  flex: 2;
}

.budget__picker {
  max-height: 60vh;
  display: flex;
  flex-direction: column;
}

.budget__picker-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #f2f4fa;
}

.budget__picker-body {
  overflow-y: auto;
  padding-bottom: 16px;
}

.budget__picker-item {
  padding: 13px 18px;
  font-size: 14px;
}

.budget__picker-item:active {
  background: #f5f7ff;
}
</style>
