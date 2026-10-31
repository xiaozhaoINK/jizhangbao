<template>
  <div class="page bills">
    <van-nav-bar title="账单" fixed placeholder>
      <template #right>
        <van-icon name="search" size="18" @click="showSearch = !showSearch" />
      </template>
    </van-nav-bar>

    <div class="bills__header">
      <MonthSwitcher v-model="month" @pick="showMonthPicker = true" />
      <div class="bills__summary">
        <div>
          <span>支出</span>
          <strong class="num">¥{{ money(summary.expense) }}</strong>
        </div>
        <div>
          <span>收入</span>
          <strong class="num">¥{{ money(summary.income) }}</strong>
        </div>
        <div>
          <span>结余</span>
          <strong class="num">¥{{ money(summary.balance) }}</strong>
        </div>
        <div>
          <span>笔数</span>
          <strong class="num">{{ summary.count || 0 }}</strong>
        </div>
      </div>
    </div>

    <van-search
      v-if="showSearch"
      v-model="keyword"
      placeholder="搜索备注 / AI 原始输入"
      shape="round"
      @search="reload"
      @clear="reload"
    />

    <van-dropdown-menu active-color="#4f6ef7" class="bills__filter">
      <van-dropdown-item v-model="type" :options="typeOptions" @change="reload" />
      <van-dropdown-item v-model="categoryId" :options="categoryOptions" @change="reload" />
    </van-dropdown-menu>

    <van-pull-refresh v-model="refreshing" @refresh="reload">
      <div class="bills__list">
        <template v-if="groups.length">
          <div v-for="group in groups" :key="group.date" class="bills__group">
            <div class="bills__group-head">
              <span>{{ friendlyDate(group.date) }} · {{ weekdayOf(group.date) }}</span>
              <span class="num">
                支出 ¥{{ money(group.expense) }}
                <template v-if="group.income > 0"> / 收入 ¥{{ money(group.income) }}</template>
              </span>
            </div>
            <van-swipe-cell v-for="item in group.items" :key="item.id">
              <div class="bills__row" @click="router.push(`/bill/${item.id}/edit`)">
                <BillItem :item="item" />
              </div>
              <template #right>
                <van-button square type="danger" text="删除" class="bills__delete" @click="remove(item)" />
              </template>
            </van-swipe-cell>
          </div>
          <div v-if="hasMore" class="bills__more" @click="loadMore">
            {{ loading ? '加载中…' : '加载更多' }}
          </div>
          <div v-else class="bills__end">— 已经到底啦 —</div>
        </template>
        <van-empty v-else-if="!loading" image="search" description="这个月还没有账单" />
      </div>
    </van-pull-refresh>

    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-date-picker
        :model-value="pickerValue"
        title="选择月份"
        :columns-type="['year', 'month']"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { billApi, categoryApi } from '@/api'
import { currentMonth, friendlyDate, money, monthRange, weekdayOf } from '@/utils/format'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import BillItem from '@/components/BillItem.vue'

const router = useRouter()

const month = ref(currentMonth())
const summary = ref({})
const bills = ref([])
const page = ref(1)
const size = 30
const total = ref(0)
const loading = ref(false)
const refreshing = ref(false)
const showSearch = ref(false)
const keyword = ref('')
const type = ref('')
const categoryId = ref('')
const categoryOptions = ref([{ text: '全部分类', value: '' }])
const showMonthPicker = ref(false)
const maxDate = new Date()
const minDate = new Date(new Date().getFullYear() - 3, 0, 1)

const typeOptions = [
  { text: '全部类型', value: '' },
  { text: '只看支出', value: 'EXPENSE' },
  { text: '只看收入', value: 'INCOME' }
]

const hasMore = computed(() => bills.value.length < total.value)

const pickerValue = computed(() => {
  const [y, m] = month.value.split('-').map(Number)
  return [String(y), String(m).padStart(2, '0')]
})

const groups = computed(() => {
  const map = new Map()
  for (const item of bills.value) {
    if (!map.has(item.billDate)) {
      map.set(item.billDate, { date: item.billDate, items: [], expense: 0, income: 0 })
    }
    const group = map.get(item.billDate)
    group.items.push(item)
    if (item.type === 'INCOME') group.income += Number(item.amount)
    else group.expense += Number(item.amount)
  }
  return Array.from(map.values())
})

const loadCategories = async () => {
  try {
    const flat = await categoryApi.flat()
    const options = [{ text: '全部分类', value: '' }]
    for (const item of flat || []) {
      options.push({
        text: item.parentId > 0 ? `　${item.name}` : item.name,
        value: String(item.id)
      })
    }
    categoryOptions.value = options
  } catch (e) {
    // 忽略
  }
}

const fetchPage = async (reset = false) => {
  if (loading.value) return
  loading.value = true
  if (reset) page.value = 1
  const { startDate, endDate } = monthRange(month.value)
  try {
    const data = await billApi.page({
      page: page.value,
      size,
      startDate,
      endDate,
      type: type.value || undefined,
      categoryId: categoryId.value || undefined,
      keyword: keyword.value || undefined
    })
    const records = data?.records || []
    bills.value = reset ? records : [...bills.value, ...records]
    total.value = data?.total || 0
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const loadSummary = async () => {
  const { startDate, endDate } = monthRange(month.value)
  try {
    summary.value = (await billApi.summary({ startDate, endDate })) || {}
  } catch (e) {
    summary.value = {}
  }
}

const reload = async () => {
  await Promise.all([fetchPage(true), loadSummary()])
}

const loadMore = async () => {
  page.value += 1
  await fetchPage(false)
}

const remove = async (item) => {
  try {
    await showConfirmDialog({
      title: '删除这笔记录？',
      message: `${item.categoryName} ¥${money(item.amount)}，删除后不可恢复`,
      confirmButtonColor: '#ff5b5b'
    })
    await billApi.remove(item.id)
    showToast({ message: '已删除', position: 'top' })
    await reload()
  } catch (e) {
    // 取消或失败
  }
}

const onMonthConfirm = ({ selectedValues }) => {
  month.value = selectedValues.join('-')
  showMonthPicker.value = false
}

watch(month, reload)

onMounted(async () => {
  await loadCategories()
  await reload()
})
</script>

<style scoped>
.bills__header {
  background: linear-gradient(150deg, #4f6ef7, #6f5cf6);
  color: #fff;
  padding: 6px 16px 18px;
  border-radius: 0 0 20px 20px;
}

.bills__summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin-top: 8px;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 12px;
  padding: 10px 0;
}

.bills__summary div {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.bills__summary span {
  font-size: 11px;
  opacity: 0.82;
}

.bills__summary strong {
  font-size: 13px;
}

.bills__filter {
  margin: 8px 14px 0;
  border-radius: 10px;
  overflow: hidden;
}

.bills__list {
  padding: 4px 14px 20px;
}

.bills__group {
  background: #fff;
  border-radius: 14px;
  padding: 4px 14px 6px;
  margin-top: 10px;
  box-shadow: var(--jzb-shadow);
  overflow: hidden;
}

.bills__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #8b95b3;
  padding: 10px 0 2px;
}

.bills__row {
  border-top: 1px solid #f2f4fa;
}

.bills__delete {
  height: 100%;
}

.bills__more,
.bills__end {
  text-align: center;
  font-size: 12px;
  color: #b4bcd4;
  padding: 16px 0;
}
</style>
