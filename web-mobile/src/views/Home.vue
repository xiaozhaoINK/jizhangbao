<template>
  <div class="page home">
    <div class="home__header">
      <div class="home__top">
        <div class="home__hello">
          <p class="home__greet">{{ greeting }}，{{ userStore.nickname }}</p>
          <p class="home__date">{{ todayText }}</p>
        </div>
        <van-icon name="bell" size="20" color="#fff" @click="go('/report')" />
      </div>

      <MonthSwitcher v-model="month" @pick="showMonthPicker = true" />

      <div class="home__amount">
        <span class="home__amount-label">本月支出</span>
        <div class="home__amount-value num">
          <span class="home__currency">¥</span>{{ money(overview.monthExpense) }}
        </div>
        <div class="home__mom">
          <van-icon :name="momDirection === 'up' ? 'arrow-up' : 'arrow-down'" size="10" />
          {{ momLabel }} · 日均 {{ yuan(overview.avgDailyExpense) }}
        </div>
      </div>

      <div class="home__meta">
        <div class="home__meta-item">
          <span>本月收入</span>
          <strong class="num">¥{{ money(overview.monthIncome) }}</strong>
        </div>
        <div class="home__meta-divider"></div>
        <div class="home__meta-item">
          <span>本月结余</span>
          <strong class="num">¥{{ money(overview.monthBalance) }}</strong>
        </div>
        <div class="home__meta-divider"></div>
        <div class="home__meta-item">
          <span>连续记账</span>
          <strong class="num">{{ overview.continuousDays || 0 }} 天</strong>
        </div>
      </div>
    </div>

    <div class="home__quick">
      <div v-for="action in quickActions" :key="action.label" class="home__quick-item" @click="go(action.path)">
        <div class="home__quick-icon" :style="{ background: action.bg }">
          <van-icon :name="action.icon" size="19" :color="action.color" />
        </div>
        <span>{{ action.label }}</span>
      </div>
    </div>

    <van-notice-bar
      v-if="alertText"
      left-icon="warning-o"
      color="#ff9f0a"
      background="#fff8e6"
      :text="alertText"
      wrapable
      class="home__notice"
      @click="go('/budget')"
    />

    <div v-if="hasBudget" class="card">
      <div class="card__title">
        <span>本月预算</span>
        <span class="more" @click="go('/budget')">管理 ›</span>
      </div>
      <div class="row--between budget__line">
        <span class="num">已用 ¥{{ money(budget.totalUsed) }}</span>
        <span class="text-weak num">预算 ¥{{ money(budget.totalBudget) }}</span>
      </div>
      <van-progress
        :percentage="Math.min(100, Math.round(budget.usageRate || 0))"
        :color="progressColor"
        track-color="#eef1f9"
        :show-pivot="false"
        stroke-width="8"
        class="budget__progress"
      />
      <div class="row--between budget__foot">
        <span :class="budget.usageRate >= 100 ? 'text-expense' : 'text-weak'">
          {{ budget.usageRate >= 100 ? '已超支' : '剩余' }}
          ¥{{ money(Math.abs(budget.totalRemaining || 0)) }}
          （{{ percent(budget.usageRate) }}）
        </span>
        <span class="text-weak">剩余 {{ budget.daysLeft }} 天 · 日均可用 ¥{{ money(budget.dailyAllowance) }}</span>
      </div>
    </div>

    <div class="card">
      <div class="card__title">
        <span>支出构成</span>
        <span class="more" @click="go('/stats')">查看统计 ›</span>
      </div>
      <template v-if="categoryStats.length">
        <div class="home__chart">
          <EChart :option="donutOption" height="180px" />
        </div>
        <div class="home__cats">
          <div v-for="(item, index) in categoryStats.slice(0, 4)" :key="item.name" class="home__cat">
            <i class="home__dot" :style="{ background: palette[index % palette.length] }"></i>
            <span class="ellipsis">{{ item.name }}</span>
            <span class="text-weak num">{{ percent(item.percent) }}</span>
            <span class="num">¥{{ money(item.amount) }}</span>
          </div>
        </div>
      </template>
      <van-empty v-else image="search" description="本月还没有支出记录" image-size="72" />
    </div>

    <div class="card">
      <div class="card__title">
        <span>最近账单</span>
        <span class="more" @click="go('/bills')">全部 ›</span>
      </div>
      <template v-if="recentBills.length">
        <BillItem
          v-for="item in recentBills"
          :key="item.id"
          :item="item"
          @click="go(`/bill/${item.id}/edit`)"
        />
      </template>
      <van-empty v-else image="search" description="还没有账单，记一笔试试" image-size="72" />
    </div>

    <div class="fab" @click="go('/bill/add')">
      <van-icon name="plus" size="24" color="#fff" />
    </div>

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
import { billApi, budgetApi, statsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { currentMonth, money, monthRange, percent, yuan } from '@/utils/format'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import BillItem from '@/components/BillItem.vue'
import EChart from '@/components/EChart.vue'

defineOptions({ name: 'Home' })

const router = useRouter()
const userStore = useUserStore()

const month = ref(currentMonth())
const overview = ref({})
const budget = ref({})
const categoryStats = ref([])
const recentBills = ref([])
const showMonthPicker = ref(false)
const maxDate = new Date()
const minDate = new Date(new Date().getFullYear() - 2, 0, 1)

const palette = ['#4F6EF7', '#FF7A45', '#00B578', '#9254DE', '#FFC53D', '#36CFC9']

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const now = new Date()
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 ${names[now.getDay()]}`
})

const quickActions = [
  { label: '记一笔', icon: 'edit', path: '/bill/add', bg: '#eef1ff', color: '#4f6ef7' },
  { label: 'AI 记账', icon: 'chat-o', path: '/ai', bg: '#f0ebff', color: '#7c5cff' },
  { label: '预算', icon: 'chart-trending-o', path: '/budget', bg: '#e8f8f1', color: '#00b578' },
  { label: '消费洞察', icon: 'notes-o', path: '/report', bg: '#fff4e5', color: '#ff9f0a' }
]

const hasBudget = computed(() => Number(budget.value?.totalBudget || 0) > 0)
const momDirection = computed(() => (Number(overview.value.expenseMoM || 0) >= 0 ? 'up' : 'down'))
const momLabel = computed(() => {
  const value = Number(overview.value.expenseMoM || 0)
  if (!value) return '与上月持平'
  return `较上月${value > 0 ? '增加' : '减少'} ${percent(Math.abs(value))}`
})
const progressColor = computed(() => {
  const rate = Number(budget.value?.usageRate || 0)
  if (rate >= 100) return '#ff5b5b'
  if (rate >= 80) return '#ff9f0a'
  return '#4f6ef7'
})

const alertText = computed(() => budget.value?.alerts?.[0]?.message || '')

const donutOption = computed(() => ({
  color: palette,
  tooltip: {
    trigger: 'item',
    formatter: '{b}：¥{c}（{d}%）'
  },
  series: [
    {
      type: 'pie',
      radius: ['58%', '82%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
      label: { show: false },
      labelLine: { show: false },
      data: categoryStats.value.map((item) => ({ name: item.name, value: Number(item.amount) }))
    }
  ]
}))

const pickerValue = computed(() => {
  const [y, m] = month.value.split('-').map(Number)
  return [String(y), String(m).padStart(2, '0')]
})

const go = (path) => router.push(path)

const onMonthConfirm = ({ selectedValues }) => {
  const [y, m] = selectedValues
  month.value = `${y}-${m}`
  showMonthPicker.value = false
}

const load = async () => {
  const { startDate, endDate } = monthRange(month.value)
  try {
    const [overviewData, budgetData, categories, recent] = await Promise.all([
      statsApi.overview(month.value),
      budgetApi.status(month.value).catch(() => ({})),
      statsApi.category({ startDate, endDate, type: 'EXPENSE', level: 'parent' }),
      billApi.recent(6)
    ])
    overview.value = overviewData || {}
    budget.value = budgetData || {}
    categoryStats.value = categories || []
    recentBills.value = recent || []
  } catch (e) {
    // 错误提示由拦截器处理
  }
}

watch(month, load)
onMounted(load)
</script>

<style scoped>
.home__header {
  background: linear-gradient(150deg, #4f6ef7 0%, #6f5cf6 62%, #8a6cff 100%);
  padding: 18px 16px 26px;
  border-radius: 0 0 24px 24px;
  color: #fff;
}

.home__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.home__greet {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.home__date {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.8;
}

.home__amount {
  text-align: center;
  padding: 8px 0 4px;
}

.home__amount-label {
  font-size: 12px;
  opacity: 0.85;
}

.home__amount-value {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 0.5px;
  line-height: 1.2;
  margin-top: 2px;
}

.home__currency {
  font-size: 18px;
  margin-right: 4px;
  opacity: 0.9;
}

.home__mom {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.88;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.16);
  padding: 3px 10px;
  border-radius: 999px;
}

.home__meta {
  margin-top: 18px;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 14px;
  padding: 12px 0;
}

.home__meta-item {
  flex: 1;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.home__meta-item span {
  font-size: 11px;
  opacity: 0.82;
}

.home__meta-item strong {
  font-size: 15px;
}

.home__meta-divider {
  width: 1px;
  height: 24px;
  background: rgba(255, 255, 255, 0.24);
}

.home__quick {
  display: flex;
  background: #fff;
  margin: -16px 14px 0;
  border-radius: 14px;
  box-shadow: var(--jzb-shadow);
  padding: 14px 4px;
  position: relative;
  z-index: 2;
}

.home__quick-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4a5578;
}

.home__quick-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.home__notice {
  margin: 12px 14px 0;
  border-radius: 10px;
}

.budget__line {
  font-size: 13px;
  margin-bottom: 8px;
}

.budget__progress {
  margin: 4px 0 10px;
}

.budget__foot {
  font-size: 12px;
}

.home__chart {
  margin: -6px 0 4px;
}

.home__cats {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.home__cat {
  display: grid;
  grid-template-columns: 8px 1fr 48px 84px;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.home__cat .num:last-child {
  text-align: right;
}

.home__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.fab {
  position: fixed;
  right: 18px;
  bottom: calc(76px + env(safe-area-inset-bottom));
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4f6ef7, #7c5cff);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(79, 110, 247, 0.4);
  z-index: 20;
}
</style>
