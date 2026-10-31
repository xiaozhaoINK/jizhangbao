<template>
  <div class="page stats">
    <van-nav-bar title="统计分析" fixed placeholder />

    <div class="stats__header">
      <MonthSwitcher v-model="month" @pick="showMonthPicker = true" />
      <div class="stats__tabs">
        <span
          v-for="tab in tabs"
          :key="tab.key"
          class="stats__tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </span>
      </div>
    </div>

    <!-- 趋势 -->
    <template v-if="activeTab === 'trend'">
      <div class="card">
        <div class="card__title">
          <span>本月收支趋势</span>
          <span class="more num">
            支出 ¥{{ money(monthSummary.expense) }} / 收入 ¥{{ money(monthSummary.income) }}
          </span>
        </div>
        <EChart :option="trendOption" height="240px" />
      </div>

      <div class="card">
        <div class="card__title">
          <span>近 6 个月对比</span>
        </div>
        <EChart :option="monthBarOption" height="240px" />
      </div>
    </template>

    <!-- 分类 -->
    <template v-else-if="activeTab === 'category'">
      <div class="card">
        <div class="stats__switches">
          <van-tabs v-model:active="categoryType" type="card" shrink @change="loadCategories">
            <van-tab title="支出" name="EXPENSE" />
            <van-tab title="收入" name="INCOME" />
          </van-tabs>
          <van-tabs v-model:active="categoryLevel" type="card" shrink @change="loadCategories">
            <van-tab title="大类" name="parent" />
            <van-tab title="二级" name="child" />
          </van-tabs>
        </div>
        <template v-if="categories.length">
          <EChart :option="categoryPieOption" height="230px" />
          <div class="stats__rank">
            <div v-for="(item, index) in categories" :key="item.categoryId" class="stats__rank-item">
              <span class="stats__rank-index">{{ index + 1 }}</span>
              <div class="stats__rank-main">
                <div class="row--between">
                  <span class="ellipsis">{{ item.name }}</span>
                  <span class="num">¥{{ money(item.amount) }}</span>
                </div>
                <van-progress
                  :percentage="Math.max(2, Math.round(item.percent))"
                  :color="palette[index % palette.length]"
                  :show-pivot="false"
                  track-color="#f2f4fa"
                  stroke-width="6"
                />
                <div class="stats__rank-sub">
                  <span>{{ percent(item.percent) }}</span>
                  <span>{{ item.count }} 笔 · 笔均 ¥{{ money(item.avgAmount) }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>
        <van-empty v-else description="本月没有该类型的数据" image-size="70" />
      </div>
    </template>

    <!-- 日历 -->
    <template v-else>
      <div class="card">
        <div class="card__title">
          <span>每日支出热力</span>
          <span class="more">颜色越深花得越多</span>
        </div>
        <div class="stats__calendar">
          <div v-for="week in weekNames" :key="week" class="stats__calendar-head">{{ week }}</div>
          <div v-for="blank in leadingBlanks" :key="`b-${blank}`" class="stats__day is-blank"></div>
          <div
            v-for="day in calendar"
            :key="day.date"
            class="stats__day"
            :class="[`level-${day.level}`, { today: day.date === todayDate }]"
            @click="showDay(day)"
          >
            <span class="num">{{ day.day }}</span>
            <i v-if="Number(day.expense) > 0" class="stats__day-dot"></i>
          </div>
        </div>
        <div class="stats__legend">
          <span>少</span>
          <i v-for="level in 5" :key="level" :class="`level-${level - 1}`"></i>
          <span>多</span>
        </div>
        <div class="stats__calendar-foot num">
          本月支出 ¥{{ money(monthSummary.expense) }} · 记账 {{ calendar.filter((d) => Number(d.expense) + Number(d.income) > 0).length }} 天
        </div>
      </div>
    </template>

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
import { showDialog } from 'vant'
import { billApi, statsApi } from '@/api'
import { currentMonth, money, monthRange, percent } from '@/utils/format'
import MonthSwitcher from '@/components/MonthSwitcher.vue'
import EChart from '@/components/EChart.vue'

const tabs = [
  { key: 'trend', label: '收支趋势' },
  { key: 'category', label: '分类构成' },
  { key: 'calendar', label: '消费日历' }
]

const palette = ['#4F6EF7', '#FF7A45', '#00B578', '#9254DE', '#FFC53D', '#36CFC9', '#F759AB']

const activeTab = ref('trend')
const month = ref(currentMonth())
const trend = ref([])
const months = ref([])
const categories = ref([])
const calendar = ref([])
const monthSummary = ref({})
const categoryType = ref('EXPENSE')
const categoryLevel = ref('parent')
const showMonthPicker = ref(false)
const maxDate = new Date()
const minDate = new Date(new Date().getFullYear() - 3, 0, 1)
const weekNames = ['日', '一', '二', '三', '四', '五', '六']

const todayDate = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
    now.getDate()
  ).padStart(2, '0')}`
})

const pickerValue = computed(() => {
  const [y, m] = month.value.split('-').map(Number)
  return [String(y), String(m).padStart(2, '0')]
})

const leadingBlanks = computed(() => {
  if (!calendar.value.length) return 0
  const [y, m] = month.value.split('-').map(Number)
  return new Date(y, m - 1, 1).getDay()
})

const trendOption = computed(() => ({
  color: ['#FF5B5B', '#00B578'],
  grid: { left: 8, right: 12, top: 34, bottom: 22, containLabel: true },
  tooltip: { trigger: 'axis', valueFormatter: (v) => `¥${Number(v || 0).toFixed(2)}` },
  legend: { data: ['支出', '收入'], right: 0, bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
  xAxis: {
    type: 'category',
    data: trend.value.map((item) => item.label),
    axisLine: { lineStyle: { color: '#e8ecf6' } },
    axisLabel: { color: '#8b95b3', fontSize: 10, interval: 4 },
    axisTick: { show: false }
  },
  // 收入（工资）与日常支出量级差异很大，用双 Y 轴避免支出曲线被压成一条直线
  yAxis: [
    {
      type: 'value',
      name: '支出',
      nameTextStyle: { color: '#8b95b3', fontSize: 10 },
      splitLine: { lineStyle: { color: '#f2f4fa' } },
      axisLabel: { color: '#8b95b3', fontSize: 10 }
    },
    {
      type: 'value',
      name: '收入',
      nameTextStyle: { color: '#8b95b3', fontSize: 10 },
      splitLine: { show: false },
      axisLabel: { color: '#8b95b3', fontSize: 10 }
    }
  ],
  series: [
    {
      name: '支出',
      type: 'line',
      yAxisIndex: 0,
      smooth: true,
      symbol: 'none',
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(255,91,91,0.18)' },
            { offset: 1, color: 'rgba(255,91,91,0)' }
          ]
        }
      },
      data: trend.value.map((item) => Number(item.expense))
    },
    {
      name: '收入',
      type: 'line',
      yAxisIndex: 1,
      smooth: true,
      symbol: 'none',
      data: trend.value.map((item) => Number(item.income))
    }
  ]
}))

const monthBarOption = computed(() => ({
  color: ['#4F6EF7', '#FF9F0A'],
  grid: { left: 8, right: 12, top: 30, bottom: 22, containLabel: true },
  tooltip: { trigger: 'axis', valueFormatter: (v) => `¥${Number(v || 0).toFixed(2)}` },
  legend: { data: ['支出', '收入'], right: 0, bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
  xAxis: {
    type: 'category',
    data: months.value.map((item) => item.label),
    axisLine: { lineStyle: { color: '#e8ecf6' } },
    axisLabel: { color: '#8b95b3', fontSize: 10 },
    axisTick: { show: false }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#f2f4fa' } },
    axisLabel: { color: '#8b95b3', fontSize: 10 }
  },
  series: [
    {
      name: '支出',
      type: 'bar',
      barWidth: 12,
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      data: months.value.map((item) => Number(item.expense))
    },
    {
      name: '收入',
      type: 'bar',
      barWidth: 12,
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      data: months.value.map((item) => Number(item.income))
    }
  ]
}))

const categoryPieOption = computed(() => ({
  color: palette,
  tooltip: { trigger: 'item', formatter: '{b}：¥{c}（{d}%）' },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '50%'],
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
      label: {
        formatter: '{b}\n{d}%',
        fontSize: 10,
        color: '#6b7699'
      },
      labelLine: { length: 8, length2: 8 },
      data: categories.value.slice(0, 8).map((item) => ({
        name: item.name,
        value: Number(item.amount)
      }))
    }
  ]
}))

const showDay = (day) => {
  if (!Number(day.expense) && !Number(day.income)) return
  showDialog({
    title: day.date,
    message: `支出 ¥${money(day.expense)}\n收入 ¥${money(day.income)}`
  })
}

const loadTrend = async () => {
  const { startDate, endDate } = monthRange(month.value)
  const [trendData, monthsData, summary] = await Promise.all([
    statsApi.trend({ startDate, endDate, granularity: 'day' }),
    statsApi.recentMonths(6),
    billApi.summary({ startDate, endDate })
  ])
  trend.value = trendData || []
  months.value = monthsData || []
  monthSummary.value = summary || {}
}

const loadCategories = async () => {
  const { startDate, endDate } = monthRange(month.value)
  try {
    categories.value =
      (await statsApi.category({
        startDate,
        endDate,
        type: categoryType.value,
        level: categoryLevel.value
      })) || []
  } catch (e) {
    categories.value = []
  }
}

const loadCalendar = async () => {
  const [calendarData, summary] = await Promise.all([
    statsApi.calendar(month.value),
    billApi.summary(monthRange(month.value))
  ])
  calendar.value = calendarData || []
  monthSummary.value = summary || {}
}

const load = async () => {
  try {
    if (activeTab.value === 'trend') await loadTrend()
    else if (activeTab.value === 'category') await loadCategories()
    else await loadCalendar()
  } catch (e) {
    // 拦截器已提示
  }
}

const onMonthConfirm = ({ selectedValues }) => {
  month.value = selectedValues.join('-')
  showMonthPicker.value = false
}

watch([month, activeTab], load)

onMounted(load)
</script>

<style scoped>
.stats__header {
  background: linear-gradient(150deg, #4f6ef7, #6f5cf6);
  color: #fff;
  padding: 4px 16px 16px;
  border-radius: 0 0 20px 20px;
}

.stats__tabs {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.stats__tab {
  flex: 1;
  text-align: center;
  font-size: 13px;
  padding: 7px 0;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.86);
}

.stats__tab.active {
  background: #fff;
  color: #4f6ef7;
  font-weight: 600;
}

.stats__switches {
  display: flex;
  gap: 10px;
  margin-bottom: 6px;
}

.stats__switches :deep(.van-tabs) {
  flex: 1;
}

.stats__switches :deep(.van-tabs__wrap) {
  height: 34px;
}

.stats__switches :deep(.van-tabs--card > .van-tabs__wrap) {
  height: 34px;
}

.stats__switches :deep(.van-tab) {
  font-size: 12px;
}

.stats__rank {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 8px;
}

.stats__rank-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.stats__rank-index {
  width: 18px;
  height: 18px;
  border-radius: 6px;
  background: #f2f4fa;
  color: #8b95b3;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.stats__rank-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.stats__rank-sub {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #8b95b3;
}

.stats__calendar {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
}

.stats__calendar-head {
  text-align: center;
  font-size: 11px;
  color: #8b95b3;
  padding-bottom: 2px;
}

.stats__day {
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #4a5578;
  position: relative;
  background: #f7f9fd;
}

.stats__day.is-blank {
  background: transparent;
}

.stats__day.today {
  outline: 2px solid #4f6ef7;
  outline-offset: -2px;
}

.stats__day.level-1 {
  background: #e8ecff;
  color: #4f6ef7;
}

.stats__day.level-2 {
  background: #c3ceff;
  color: #2c3f9e;
}

.stats__day.level-3 {
  background: #8f9ffb;
  color: #fff;
}

.stats__day.level-4 {
  background: #4f6ef7;
  color: #fff;
}

.stats__day-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
  margin-top: 1px;
}

.stats__legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  font-size: 11px;
  color: #8b95b3;
  margin-top: 10px;
}

.stats__legend i {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  display: inline-block;
  background: #f7f9fd;
}

.stats__legend i.level-1 {
  background: #e8ecff;
}
.stats__legend i.level-2 {
  background: #c3ceff;
}
.stats__legend i.level-3 {
  background: #8f9ffb;
}
.stats__legend i.level-4 {
  background: #4f6ef7;
}

.stats__calendar-foot {
  margin-top: 12px;
  font-size: 12px;
  color: #8b95b3;
  text-align: center;
}
</style>
