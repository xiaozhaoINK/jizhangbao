<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import ChartCard from '@/components/ChartCard.vue'
import BaseChart from '@/components/BaseChart.vue'
import CategoryIcon from '@/components/CategoryIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { statsApi } from '@/api'
import {
  buildMonthRange,
  currentYearMonth,
  formatMoney,
  formatMonthLabel,
  formatPercent,
  shiftMonth,
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

const current = currentYearMonth()

const range = ref([shiftMonth(current, -5), current])
const granularity = ref('day')
const categoryType = ref('EXPENSE')
const categoryLevel = ref('parent')
const year = ref(Number(current.slice(0, 4)))
const calendarMonth = ref(current)

const trend = ref([])
const categories = ref([])
const months = ref([])
const calendarDays = ref([])

const trendLoading = ref(false)
const categoryLoading = ref(false)
const monthsLoading = ref(false)
const calendarLoading = ref(false)

const dateRange = computed(() => {
  const [start, end] = range.value || []
  if (!start || !end) {
    const fallback = buildMonthRange(shiftMonth(current, -5), current)
    return fallback
  }
  return buildMonthRange(start, end)
})

const rangeLabel = computed(() => {
  const [start, end] = range.value || []
  if (!start || !end) return ''
  return `${formatMonthLabel(start)} - ${formatMonthLabel(end)}`
})

const yearOptions = computed(() => {
  const now = Number(current.slice(0, 4))
  return Array.from({ length: 5 }, (_, index) => now - index)
})

const categoryTotal = computed(() =>
  categories.value.reduce((sum, item) => sum + toNumber(item.amount), 0)
)

const categoryRanking = computed(() =>
  [...categories.value].sort((a, b) => toNumber(b.amount) - toNumber(a.amount)).slice(0, 8)
)

const maxCategoryAmount = computed(() =>
  categoryRanking.value.reduce((max, item) => Math.max(max, toNumber(item.amount)), 0)
)

const trendOption = computed(() => {
  if (!trend.value.length) return null
  return {
    color: [CHART_COLORS.income, CHART_COLORS.expense],
    tooltip: tooltipConfig({ formatter: (params) => tooltipRows(params) }),
    legend: { ...legendBase, bottom: 0, right: 4, data: ['收入', '支出'] },
    grid: { ...gridBase, top: 40, bottom: 30, left: 6, right: 14 },
    xAxis: {
      type: 'category',
      boundaryGap: granularity.value === 'month',
      data: trend.value.map((item) => item.label),
      axisLine: axisLineStyle,
      axisTick: axisTickStyle,
      axisLabel: axisLabelStyle
    },
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
        lineStyle: { width: 2.2 },
        areaStyle: { color: areaGradient(CHART_COLORS.income) },
        data: trend.value.map((item) => toNumber(item.income))
      },
      {
        name: '支出',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2.2 },
        areaStyle: { color: areaGradient(CHART_COLORS.expense) },
        data: trend.value.map((item) => toNumber(item.expense))
      }
    ]
  }
})

const categoryOption = computed(() => {
  if (!categories.value.length) return null
  const isExpense = categoryType.value === 'EXPENSE'
  return {
    color: PALETTE,
    tooltip: tooltipConfig({ trigger: 'item', formatter: pieTooltip, axisPointer: undefined }),
    title: {
      show: false
    },
    series: [
      {
        name: isExpense ? '支出分类' : '收入分类',
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '50%'],
        itemStyle: { borderColor: '#FFFFFF', borderWidth: 2, borderRadius: 4 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { scale: true, scaleSize: 5 },
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
        barWidth: 12,
        barGap: '32%',
        itemStyle: { color: CHART_COLORS.income, borderRadius: [3, 3, 0, 0] },
        data: months.value.map((item) => toNumber(item.income))
      },
      {
        name: '支出',
        type: 'bar',
        barWidth: 12,
        itemStyle: { color: CHART_COLORS.expense, borderRadius: [3, 3, 0, 0] },
        data: months.value.map((item) => toNumber(item.expense))
      }
    ]
  }
})

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

const LEVEL_COLORS = [
  '#f6f8fc',
  'rgba(79, 110, 247, 0.16)',
  'rgba(79, 110, 247, 0.32)',
  'rgba(79, 110, 247, 0.52)',
  'rgba(79, 110, 247, 0.78)'
]

function levelColor(level) {
  return LEVEL_COLORS[level] || LEVEL_COLORS[0]
}

const calendarCells = computed(() => {
  const [yearValue, monthValue] = calendarMonth.value.split('-').map(Number)
  if (!yearValue || !monthValue) return []
  const firstDay = new Date(yearValue, monthValue - 1, 1)
  const leading = (firstDay.getDay() + 6) % 7
  const totalDays = new Date(yearValue, monthValue, 0).getDate()
  const statMap = new Map(calendarDays.value.map((item) => [toNumber(item.day), item]))
  const cells = []

  for (let index = 0; index < leading; index += 1) {
    cells.push({ key: `blank-${index}`, blank: true })
  }

  for (let day = 1; day <= totalDays; day += 1) {
    const stat = statMap.get(day)
    cells.push({
      key: `day-${day}`,
      day,
      level: stat ? toNumber(stat.level) : 0,
      income: toNumber(stat?.income),
      expense: toNumber(stat?.expense),
      count: toNumber(stat?.count)
    })
  }

  return cells
})

const calendarSummary = computed(() =>
  calendarDays.value.reduce(
    (acc, item) => ({
      expense: acc.expense + toNumber(item.expense),
      income: acc.income + toNumber(item.income),
      count: acc.count + toNumber(item.count)
    }),
    { expense: 0, income: 0, count: 0 }
  )
)

async function loadTrend() {
  trendLoading.value = true
  try {
    trend.value = await statsApi.trend({ ...dateRange.value, granularity: granularity.value })
  } catch (error) {
    trend.value = []
  } finally {
    trendLoading.value = false
  }
}

async function loadCategories() {
  categoryLoading.value = true
  try {
    categories.value = await statsApi.category({
      ...dateRange.value,
      type: categoryType.value,
      level: categoryLevel.value
    })
  } catch (error) {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

async function loadMonths() {
  monthsLoading.value = true
  try {
    months.value = await statsApi.monthly(year.value)
  } catch (error) {
    months.value = []
  } finally {
    monthsLoading.value = false
  }
}

async function loadCalendar() {
  calendarLoading.value = true
  try {
    calendarDays.value = await statsApi.calendar(calendarMonth.value)
  } catch (error) {
    calendarDays.value = []
  } finally {
    calendarLoading.value = false
  }
}

function reloadAll() {
  loadTrend()
  loadCategories()
}

watch(granularity, loadTrend)
watch(categoryType, loadCategories)
watch(categoryLevel, loadCategories)
watch(year, loadMonths)
watch(calendarMonth, loadCalendar)
watch(range, () => {
  loadTrend()
  loadCategories()
})

onMounted(() => {
  reloadAll()
  loadMonths()
  loadCalendar()
})
</script>

<template>
  <div class="page page-stack">
    <section class="card stats-toolbar">
      <div class="stats-toolbar__group">
        <span class="text-sm text-muted">统计区间</span>
        <el-date-picker
          v-model="range"
          type="monthrange"
          value-format="YYYY-MM"
          range-separator="至"
          start-placeholder="开始月份"
          end-placeholder="结束月份"
          :clearable="false"
          style="width: 260px"
        />
        <span class="text-sm text-muted">{{ rangeLabel }}</span>
      </div>
      <el-button :icon="Refresh" @click="reloadAll">刷新</el-button>
    </section>

    <ChartCard title="收支趋势" subtitle="按天或按月查看区间内的收入与支出变化">
      <template #extra>
        <el-radio-group v-model="granularity" size="small">
          <el-radio-button value="day">按天</el-radio-button>
          <el-radio-button value="month">按月</el-radio-button>
        </el-radio-group>
      </template>
      <BaseChart
        :option="trendOption"
        :loading="trendLoading"
        :empty="!trendLoading && !trend.length"
        height="320px"
        empty-text="该区间暂无收支数据"
      />
    </ChartCard>

    <div class="grid grid-2">
      <ChartCard title="分类占比" subtitle="查看支出或收入的结构分布">
        <template #extra>
          <el-radio-group v-model="categoryType" size="small">
            <el-radio-button value="EXPENSE">支出</el-radio-button>
            <el-radio-button value="INCOME">收入</el-radio-button>
          </el-radio-group>
          <el-radio-group v-model="categoryLevel" size="small">
            <el-radio-button value="parent">一级</el-radio-button>
            <el-radio-button value="child">二级</el-radio-button>
          </el-radio-group>
        </template>

        <div class="category-layout">
          <div class="pie-wrap">
            <BaseChart
              :option="categoryOption"
              :loading="categoryLoading"
              :empty="!categoryLoading && !categories.length"
              height="280px"
              empty-text="暂无分类数据"
            />
            <div v-if="categories.length" class="pie-center">
              <strong class="amount">{{ formatMoney(categoryTotal) }}</strong>
              <span>{{ categoryType === 'EXPENSE' ? '支出合计' : '收入合计' }}</span>
            </div>
          </div>
          <div v-if="categoryRanking.length" class="rank-list category-rank">
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
        </div>
      </ChartCard>

      <ChartCard title="年度收支对比" subtitle="按月对比全年收入与支出">
        <template #extra>
          <el-select v-model="year" size="small" style="width: 110px">
            <el-option v-for="item in yearOptions" :key="item" :label="`${item} 年`" :value="item" />
          </el-select>
        </template>
        <BaseChart
          :option="monthsOption"
          :loading="monthsLoading"
          :empty="!monthsLoading && !months.length"
          height="340px"
          empty-text="该年度暂无数据"
        />
      </ChartCard>
    </div>

    <ChartCard title="日历热力图" subtitle="按日查看支出强度，颜色越深支出越多">
      <template #extra>
        <el-date-picker
          v-model="calendarMonth"
          type="month"
          value-format="YYYY-MM"
          :clearable="false"
          size="small"
          style="width: 130px"
        />
      </template>

      <div v-loading="calendarLoading" class="calendar">
        <div class="calendar__head">
          <span class="calendar__month">{{ formatMonthLabel(calendarMonth) }}</span>
          <span class="calendar__summary">
            支出 {{ formatMoney(calendarSummary.expense) }} · 收入
            {{ formatMoney(calendarSummary.income) }} · 共 {{ calendarSummary.count }} 笔
          </span>
        </div>

        <div class="calendar__weekdays">
          <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
        </div>

        <div class="calendar__grid">
          <el-tooltip
            v-for="cell in calendarCells"
            :key="cell.key"
            placement="top"
            :disabled="cell.blank"
            :show-after="80"
          >
            <template #content>
              <template v-if="!cell.blank">
                <div class="calendar__tip-title">{{ formatMonthLabel(calendarMonth) }}{{ cell.day }}日</div>
                <div>支出：{{ formatMoney(cell.expense) }}</div>
                <div>收入：{{ formatMoney(cell.income) }}</div>
                <div>笔数：{{ cell.count }} 笔</div>
              </template>
            </template>
            <div
              v-if="!cell.blank"
              class="calendar__cell"
              :class="`calendar__cell--level-${cell.level}`"
            >
              <span class="calendar__day">{{ cell.day }}</span>
              <span v-if="cell.expense > 0" class="calendar__amount">
                {{ formatMoney(cell.expense, 0) }}
              </span>
            </div>
            <div v-else class="calendar__cell calendar__cell--blank" />
          </el-tooltip>
        </div>

        <div class="calendar__legend">
          <span>少</span>
          <i
            v-for="level in 5"
            :key="level"
            class="calendar__legend-dot"
            :style="{ background: levelColor(level - 1) }"
          />
          <span>多</span>
          <span class="calendar__legend-note">无数据表示当天没有记账</span>
        </div>
      </div>

      <EmptyState
        v-if="!calendarLoading && !calendarDays.length"
        compact
        icon="Calendar"
        title="本月还没有记账记录"
        description="记录账单后即可在日历热力图中查看每日支出强度"
      />
    </ChartCard>
  </div>
</template>

<style scoped>
.stats-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.stats-toolbar__group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.category-layout {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.category-rank {
  margin-top: 4px;
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
  font-size: 17px;
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

.calendar {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.calendar__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.calendar__month {
  font-size: 14px;
  font-weight: 600;
  color: var(--jz-text);
}

.calendar__summary {
  font-size: 12.5px;
  color: var(--jz-text-muted);
  font-variant-numeric: tabular-nums;
}

.calendar__weekdays {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
  font-size: 12px;
  color: var(--jz-text-muted);
  text-align: center;
}

.calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.calendar__cell {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
  min-height: 62px;
  padding: 8px 9px;
  border-radius: var(--jz-radius-sm);
  border: 1px solid #eef1f9;
  background: #f6f8fc;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
  cursor: default;
}

.calendar__cell:hover:not(.calendar__cell--blank) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(31, 45, 90, 0.08);
}

.calendar__cell--blank {
  background: transparent;
  border-color: transparent;
  min-height: 62px;
}

.calendar__day {
  font-size: 12.5px;
  color: var(--jz-text-secondary);
  font-variant-numeric: tabular-nums;
}

.calendar__amount {
  font-size: 11.5px;
  color: var(--jz-text-secondary);
  font-variant-numeric: tabular-nums;
}

.calendar__cell--level-1 {
  background: rgba(79, 110, 247, 0.1);
  border-color: rgba(79, 110, 247, 0.14);
}

.calendar__cell--level-2 {
  background: rgba(79, 110, 247, 0.24);
  border-color: rgba(79, 110, 247, 0.26);
}

.calendar__cell--level-3 {
  background: rgba(79, 110, 247, 0.46);
  border-color: rgba(79, 110, 247, 0.4);
}

.calendar__cell--level-4 {
  background: rgba(79, 110, 247, 0.72);
  border-color: rgba(79, 110, 247, 0.6);
}

.calendar__cell--level-2 .calendar__day,
.calendar__cell--level-2 .calendar__amount,
.calendar__cell--level-3 .calendar__day,
.calendar__cell--level-3 .calendar__amount,
.calendar__cell--level-4 .calendar__day,
.calendar__cell--level-4 .calendar__amount {
  color: #ffffff;
}

.calendar__cell--level-1 .calendar__day,
.calendar__cell--level-1 .calendar__amount {
  color: #3f5ad4;
}

.calendar__legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--jz-text-muted);
}

.calendar__legend-dot {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  background: #f6f8fc;
  border: 1px solid #eef1f9;
}

.calendar__legend-note {
  margin-left: 12px;
}

.calendar__tip-title {
  margin-bottom: 4px;
  font-weight: 600;
  color: #ffffff;
}
</style>
