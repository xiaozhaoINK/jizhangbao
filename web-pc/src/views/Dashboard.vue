<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import BaseChart from '@/components/BaseChart.vue'
import EmptyState from '@/components/EmptyState.vue'
import CategoryIcon from '@/components/CategoryIcon.vue'
import { billApi, budgetApi, statsApi } from '@/api'
import {
  currentYearMonth,
  formatDate,
  formatMoney,
  formatMonthLabel,
  formatPercent,
  monthEnd,
  monthStart,
  recentDaysRange,
  toNumber
} from '@/utils/format'
import {
  CHART_COLORS,
  PALETTE,
  areaGradient,
  axisLabelStyle,
  axisLineStyle,
  axisTickStyle,
  compactAxisLabel,
  gridBase,
  legendBase,
  pieTooltip,
  splitLineStyle,
  tooltipConfig,
  tooltipRows
} from '@/utils/chart'

const router = useRouter()
const yearMonth = currentYearMonth()

const loading = ref(true)
const overview = ref(null)
const budgetStatus = ref(null)
const trend = ref([])
const categories = ref([])
const months = ref([])
const recentBills = ref([])

const alerts = computed(() => budgetStatus.value?.alerts || [])

const monthBillCount = computed(() => toNumber(overview.value?.monthBillCount))

const lastMonthBalance = computed(
  () => toNumber(overview.value?.lastMonthIncome) - toNumber(overview.value?.lastMonthExpense)
)

const budgetUsageRate = computed(() => toNumber(overview.value?.budgetUsageRate))

const categoryTotal = computed(() =>
  categories.value.reduce((sum, item) => sum + toNumber(item.amount), 0)
)

const categoryRanking = computed(() =>
  [...categories.value]
    .sort((a, b) => toNumber(b.amount) - toNumber(a.amount))
    .slice(0, 6)
)

const maxCategoryAmount = computed(() =>
  categoryRanking.value.reduce((max, item) => Math.max(max, toNumber(item.amount)), 0)
)

const statusCards = computed(() => {
  const data = overview.value || {}
  return [
    {
      label: '本月支出',
      value: formatMoney(data.monthExpense),
      icon: 'Wallet',
      tone: 'expense',
      delta: data.expenseMoM ?? 0,
      deltaInverse: true,
      deltaLabel: '较上月',
      hint: `共 ${monthBillCount.value} 笔 · 日均 ${formatMoney(data.avgDailyExpense)}`
    },
    {
      label: '本月收入',
      value: formatMoney(data.monthIncome),
      icon: 'Money',
      tone: 'income',
      delta: data.incomeMoM ?? 0,
      deltaLabel: '较上月',
      hint: `今日收入 ${formatMoney(data.todayIncome)}`
    },
    {
      label: '本月结余',
      value: formatMoney(data.monthBalance),
      icon: 'TrendCharts',
      tone: 'primary',
      delta: null,
      hint: `上月结余 ${formatMoney(lastMonthBalance.value)}`
    },
    {
      label: '预算使用率',
      value: formatPercent(budgetUsageRate.value),
      icon: 'Odometer',
      tone: 'warning',
      delta: null,
      hint: data.budgetAmount
        ? `已用 ${formatMoney(data.budgetUsed)} / ${formatMoney(data.budgetAmount)}`
        : '尚未设置本月预算'
    }
  ]
})

const trendOption = computed(() => {
  if (!trend.value.length) return null
  return {
    color: [CHART_COLORS.income, CHART_COLORS.expense],
    tooltip: tooltipConfig({ formatter: (params) => tooltipRows(params) }),
    legend: { ...legendBase, bottom: 0, right: 4, data: ['收入', '支出'] },
    grid: { ...gridBase, top: 40, bottom: 30, left: 6, right: 14 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.value.map((item) => item.label),
      axisLine: axisLineStyle,
      axisTick: axisTickStyle,
      axisLabel: { ...axisLabelStyle, interval: 'auto' }
    },
    // 工资收入与日常支出量级差距很大，用双 Y 轴避免支出曲线被压平
    yAxis: [
      {
        type: 'value',
        name: '支出',
        nameTextStyle: { color: '#8A94AB', fontSize: 11, padding: [0, 0, 0, -8] },
        axisLine: { show: false },
        axisTick: axisTickStyle,
        axisLabel: { ...axisLabelStyle, formatter: compactAxisLabel },
        splitLine: splitLineStyle
      },
      {
        type: 'value',
        name: '收入',
        nameTextStyle: { color: '#8A94AB', fontSize: 11, padding: [0, -8, 0, 0] },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { ...axisLabelStyle, formatter: compactAxisLabel },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '收入',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2.2 },
        areaStyle: { color: areaGradient(CHART_COLORS.income) },
        emphasis: { focus: 'series' },
        data: trend.value.map((item) => toNumber(item.income))
      },
      {
        name: '支出',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2.2 },
        areaStyle: { color: areaGradient(CHART_COLORS.expense) },
        emphasis: { focus: 'series' },
        data: trend.value.map((item) => toNumber(item.expense))
      }
    ]
  }
})

const categoryOption = computed(() => {
  if (!categories.value.length) return null
  return {
    color: PALETTE,
    tooltip: tooltipConfig({
      trigger: 'item',
      formatter: pieTooltip,
      axisPointer: undefined
    }),
    title: {
      show: false
    },
    series: [
      {
        name: '支出分类',
        type: 'pie',
        radius: ['56%', '76%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#FFFFFF', borderWidth: 2, borderRadius: 4 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { scale: true, scaleSize: 4 },
        data: categories.value.map((item) => ({
          name: item.name,
          value: toNumber(item.amount),
          itemStyle: item.color ? { color: item.color } : undefined
        }))
      }
    ]
  }
})

const monthsOption = computed(() => {
  if (!months.value.length) return null
  return {
    color: [CHART_COLORS.income, CHART_COLORS.expense],
    tooltip: tooltipConfig({ formatter: (params) => tooltipRows(params) }),
    legend: { ...legendBase, bottom: 0, right: 4, data: ['收入', '支出'] },
    grid: { ...gridBase, top: 40, bottom: 30, left: 6, right: 14 },
    xAxis: {
      type: 'category',
      data: months.value.map((item) => item.label),
      axisLine: axisLineStyle,
      axisTick: axisTickStyle,
      axisLabel: axisLabelStyle
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: axisTickStyle,
      axisLabel: { ...axisLabelStyle, formatter: compactAxisLabel },
      splitLine: splitLineStyle
    },
    series: [
      {
        name: '收入',
        type: 'bar',
        barWidth: 14,
        barGap: '30%',
        itemStyle: { color: CHART_COLORS.income, borderRadius: [4, 4, 0, 0] },
        data: months.value.map((item) => toNumber(item.income))
      },
      {
        name: '支出',
        type: 'bar',
        barWidth: 14,
        itemStyle: { color: CHART_COLORS.expense, borderRadius: [4, 4, 0, 0] },
        data: months.value.map((item) => toNumber(item.expense))
      }
    ]
  }
})

function pick(result, fallback) {
  return result.status === 'fulfilled' && result.value != null ? result.value : fallback
}

async function load() {
  loading.value = true
  const trendRange = recentDaysRange(30)
  const results = await Promise.allSettled([
    statsApi.overview(yearMonth),
    budgetApi.status(yearMonth),
    statsApi.trend({ ...trendRange, granularity: 'day' }),
    statsApi.category({
      startDate: monthStart(yearMonth),
      endDate: monthEnd(yearMonth),
      type: 'EXPENSE',
      level: 'parent'
    }),
    statsApi.recentMonths(6),
    billApi.recent(5)
  ])

  overview.value = pick(results[0], null)
  budgetStatus.value = pick(results[1], null)
  trend.value = pick(results[2], [])
  categories.value = pick(results[3], [])
  months.value = pick(results[4], [])
  recentBills.value = pick(results[5], [])
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="page page-stack">
    <div v-if="alerts.length" class="alert-banner">
      <el-alert
        v-for="(alert, index) in alerts"
        :key="index"
        :type="alert.level === 'EXCEEDED' ? 'error' : 'warning'"
        :title="alert.title"
        show-icon
        :closable="false"
      >
        <div class="alert-banner__body">
          <span>{{ alert.message }}</span>
          <el-link type="primary" :underline="false" @click="router.push({ name: 'budgets' })">
            去调整预算
          </el-link>
        </div>
      </el-alert>
    </div>

    <div class="page-toolbar">
      <div class="page-toolbar__info">
        <span class="page-toolbar__month">{{ formatMonthLabel(yearMonth) }}</span>
        <span class="page-toolbar__hint">
          已连续记账 {{ overview?.continuousDays ?? 0 }} 天
          <template v-if="overview?.topCategoryName">
            · 支出最多为「{{ overview.topCategoryName }}」{{ formatMoney(overview.topCategoryAmount) }}
          </template>
        </span>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
    </div>

    <div v-loading="loading" class="grid grid-4">
      <StatCard v-for="card in statusCards" :key="card.label" v-bind="card" />
    </div>

    <div class="grid grid-2-1">
      <ChartCard title="收支趋势" subtitle="近 30 天每日收入与支出">
        <BaseChart
          :option="trendOption"
          :loading="loading"
          :empty="!loading && !trend.length"
          height="320px"
          empty-text="近 30 天还没有账单记录"
        />
      </ChartCard>

      <ChartCard title="支出结构" subtitle="本月一级分类占比">
        <div class="pie-wrap">
          <BaseChart
            :option="categoryOption"
            :loading="loading"
            :empty="!loading && !categories.length"
            height="196px"
            empty-text="本月暂无支出"
          />
          <div v-if="categories.length" class="pie-center">
            <strong class="amount">{{ formatMoney(categoryTotal) }}</strong>
            <span>本月支出</span>
          </div>
        </div>
        <div v-if="categoryRanking.length" class="rank-list">
          <div v-for="(item, index) in categoryRanking" :key="item.categoryId" class="rank-item">
            <span class="rank-item__index" :class="{ 'rank-item__index--top': index < 3 }">
              {{ index + 1 }}
            </span>
            <div class="rank-item__body">
              <div class="rank-item__head">
                <span class="inline-flex">
                  <CategoryIcon
                    :icon="item.icon"
                    :name="item.name"
                    :color="item.color || '#4F6EF7'"
                    :size="18"
                  />
                  {{ item.name }}
                </span>
                <span class="amount text-sm">{{ formatMoney(item.amount) }}</span>
              </div>
              <div class="rank-item__track">
                <div
                  class="rank-item__bar"
                  :style="{
                    width: `${Math.min(100, (toNumber(item.amount) / (maxCategoryAmount || 1)) * 100)}%`,
                    background: item.color || 'var(--jz-primary)'
                  }"
                />
              </div>
            </div>
            <span class="rank-item__percent text-xs text-muted">
              {{ formatPercent(item.percent, 1) }}
            </span>
          </div>
        </div>
        <EmptyState
          v-else-if="!loading"
          compact
          icon="PieChart"
          title="暂无分类占比"
          description="记录几笔支出后即可查看消费结构"
        />
      </ChartCard>
    </div>

    <ChartCard title="近 6 个月收支对比" subtitle="按月对比收入与支出走势">
      <BaseChart
        :option="monthsOption"
        :loading="loading"
        :empty="!loading && !months.length"
        height="280px"
        empty-text="暂无月度数据"
      />
    </ChartCard>

    <ChartCard title="最近账单" subtitle="最新 5 条记账记录">
      <template #extra>
        <el-button text type="primary" @click="router.push({ name: 'bills' })">
          查看全部账单
        </el-button>
      </template>
      <el-table
        v-loading="loading"
        :data="recentBills"
        row-key="id"
        :show-header="recentBills.length > 0"
      >
        <el-table-column label="日期" width="120">
          <template #default="{ row }">
            <span class="num text-muted">{{ formatDate(row.billDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" min-width="180">
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
        <el-table-column label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="row.remark ? '' : 'text-muted'">{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="110">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.payMethod || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="row.source === 'AI' ? 'primary' : 'info'" effect="light">
              {{ row.source === 'AI' ? 'AI 记账' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">
            <span
              class="amount num"
              :class="row.type === 'INCOME' ? 'text-income' : 'text-expense'"
            >
              {{ row.type === 'INCOME' ? '+' : '-' }}{{ formatMoney(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            compact
            icon="Tickets"
            title="还没有账单记录"
            description="可以从账单管理或 AI 助手开始记第一笔账"
          />
        </template>
      </el-table>
    </ChartCard>
  </div>
</template>

<style scoped>
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-toolbar__info {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.page-toolbar__month {
  font-size: 14px;
  font-weight: 600;
  color: var(--jz-text);
}

.page-toolbar__hint {
  font-size: 12.5px;
  color: var(--jz-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-banner__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 2px;
}

.pie-wrap {
  position: relative;
}

.pie-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  pointer-events: none;
}

.pie-center strong {
  font-size: 16px;
  font-weight: 600;
  color: var(--jz-text);
}

.pie-center span {
  font-size: 11px;
  color: var(--jz-text-muted);
}

.rank-item__percent {
  width: 48px;
  text-align: right;
  flex-shrink: 0;
}
</style>
