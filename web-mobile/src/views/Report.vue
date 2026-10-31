<template>
  <div class="page page--plain report">
    <van-nav-bar title="消费洞察" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span class="report__nav" @click="regenerate">重新生成</span>
      </template>
    </van-nav-bar>

    <div class="report__header">
      <MonthSwitcher v-model="month" />
      <div class="report__summary">
        <div class="report__summary-item">
          <span>支出</span>
          <strong class="num">¥{{ money(report.totalExpense) }}</strong>
        </div>
        <div class="report__summary-item">
          <span>收入</span>
          <strong class="num">¥{{ money(report.totalIncome) }}</strong>
        </div>
        <div class="report__summary-item">
          <span>结余</span>
          <strong class="num">¥{{ money(report.balance) }}</strong>
        </div>
        <div class="report__summary-item">
          <span>笔数</span>
          <strong class="num">{{ report.billCount || 0 }}</strong>
        </div>
      </div>
    </div>

    <div v-if="loading" class="report__loading">
      <van-loading size="22" vertical>正在生成分析报告…</van-loading>
    </div>

    <template v-else-if="report.content">
      <div class="card report__summary-text">
        <div class="report__gen">
          <van-tag :type="report.generator === 'LLM' ? 'primary' : 'warning'" plain size="medium">
            {{ report.generator === 'LLM' ? '大模型生成' : '规则引擎生成' }}
          </van-tag>
          <span class="text-weak">{{ report.yearMonth }}</span>
        </div>
        <p class="report__lead">{{ leadText }}</p>
      </div>

      <div v-if="report.insights?.length" class="card">
        <div class="card__title"><span>关键发现</span></div>
        <div class="report__items">
          <div v-for="(item, index) in report.insights" :key="index" class="report__item">
            <div class="report__item-icon" :style="{ background: typeColor(item.type).bg }">
              <van-icon :name="typeIcon(item.type)" :color="typeColor(item.type).fg" size="16" />
            </div>
            <div class="report__item-main">
              <p class="report__item-title">
                {{ item.title }}
                <van-tag :type="typeTag(item.type)" plain size="mini">{{ typeLabel(item.type) }}</van-tag>
              </p>
              <p class="report__item-detail">{{ item.detail }}</p>
              <p v-if="item.evidence" class="report__item-evidence">{{ item.evidence }}</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="report.suggestions?.length" class="card">
        <div class="card__title"><span>省钱建议</span></div>
        <div class="report__items">
          <div v-for="(item, index) in report.suggestions" :key="index" class="report__item">
            <div class="report__item-icon" style="background: #e8f8f1">
              <van-icon name="guide-o" color="#00b578" size="16" />
            </div>
            <div class="report__item-main">
              <p class="report__item-title">{{ item.title }}</p>
              <p class="report__item-detail">{{ item.detail }}</p>
              <p v-if="item.evidence" class="report__item-evidence">{{ item.evidence }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="card report__history">
        <div class="card__title"><span>历史报告</span></div>
        <div class="report__history-list">
          <span
            v-for="item in history"
            :key="item.id"
            class="report__history-item"
            :class="{ active: item.yearMonth === month }"
            @click="month = item.yearMonth"
          >
            {{ item.yearMonth }}
          </span>
          <span v-if="!history.length" class="text-weak">暂无历史报告</span>
        </div>
      </div>
    </template>

    <van-empty v-else description="本月还没有可分析的账单" image-size="80" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { reportApi } from '@/api'
import { currentMonth, money } from '@/utils/format'
import MonthSwitcher from '@/components/MonthSwitcher.vue'

const router = useRouter()

const month = ref(currentMonth())
const report = ref({})
const history = ref([])
const loading = ref(false)

const leadText = computed(() => {
  if (!report.value.content) return ''
  const lines = report.value.content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
  return lines[0] || ''
})

const typeColor = (type) => {
  switch (type) {
    case 'WARNING':
      return { bg: '#fff4e5', fg: '#ff9f0a' }
    case 'GOOD':
      return { bg: '#e8f8f1', fg: '#00b578' }
    case 'TIP':
      return { bg: '#eef1ff', fg: '#4f6ef7' }
    default:
      return { bg: '#f2f4fa', fg: '#8b95b3' }
  }
}

const typeIcon = (type) => {
  switch (type) {
    case 'WARNING':
      return 'warning-o'
    case 'GOOD':
      return 'passed'
    case 'TIP':
      return 'bulb-o'
    default:
      return 'info-o'
  }
}

const typeTag = (type) => {
  switch (type) {
    case 'WARNING':
      return 'warning'
    case 'GOOD':
      return 'success'
    case 'TIP':
      return 'primary'
    default:
      return 'default'
  }
}

const typeLabel = (type) => {
  switch (type) {
    case 'WARNING':
      return '需注意'
    case 'GOOD':
      return '表现好'
    case 'TIP':
      return '建议'
    default:
      return '信息'
  }
}

const load = async () => {
  loading.value = true
  try {
    report.value = (await reportApi.monthly(month.value)) || {}
    history.value = (await reportApi.list(6)) || []
  } catch (e) {
    report.value = {}
  } finally {
    loading.value = false
  }
}

const regenerate = async () => {
  loading.value = true
  try {
    report.value = await reportApi.generate(month.value)
    history.value = (await reportApi.list(6)) || []
    showToast({ message: '报告已更新', position: 'top' })
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

watch(month, load)
onMounted(load)
</script>

<style scoped>
.report__nav {
  font-size: 13px;
  color: #4f6ef7;
}

.report__header {
  background: linear-gradient(150deg, #4f6ef7, #6f5cf6);
  color: #fff;
  padding: 4px 16px 18px;
  border-radius: 0 0 22px 22px;
}

.report__summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: rgba(255, 255, 255, 0.14);
  border-radius: 12px;
  padding: 10px 0;
  margin-top: 8px;
}

.report__summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.report__summary-item span {
  font-size: 11px;
  opacity: 0.82;
}

.report__summary-item strong {
  font-size: 13px;
}

.report__loading {
  padding: 60px 0;
  text-align: center;
}

.report__gen {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 12px;
}

.report__lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #3b4670;
}

.report__items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report__item {
  display: flex;
  gap: 10px;
}

.report__item-icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.report__item-main {
  flex: 1;
  min-width: 0;
}

.report__item-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.report__item-detail {
  margin: 4px 0 0;
  font-size: 13px;
  color: #5a6488;
  line-height: 1.7;
}

.report__item-evidence {
  margin: 6px 0 0;
  font-size: 11px;
  color: #8b95b3;
  background: #f7f9fd;
  padding: 6px 10px;
  border-radius: 8px;
}

.report__history-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
}

.report__history-item {
  padding: 6px 12px;
  border-radius: 999px;
  background: #f3f5fb;
  color: #6b7699;
}

.report__history-item.active {
  background: #eef1ff;
  color: #4f6ef7;
  font-weight: 600;
}
</style>
