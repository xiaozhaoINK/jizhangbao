<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Refresh } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import { reportApi } from '@/api'
import { currentYearMonth, formatDateTime, formatMoney, formatMonthLabel, toNumber } from '@/utils/format'
import { aiEngineLabel } from '@/utils/labels'
import { renderMarkdown } from '@/utils/markdown'

const yearMonth = ref(currentYearMonth())
const report = ref(null)
const loading = ref(false)
const generating = ref(false)
const history = ref([])
const historyLoading = ref(false)

const contentHtml = computed(() => renderMarkdown(report.value?.content))

const stats = computed(() => {
  const data = report.value || {}
  return [
    { label: '收入', value: formatMoney(data.totalIncome), tone: 'income' },
    { label: '支出', value: formatMoney(data.totalExpense), tone: 'expense' },
    { label: '结余', value: formatMoney(data.balance), tone: 'primary' },
    { label: '账单笔数', value: `${toNumber(data.billCount)} 笔`, tone: 'muted' }
  ]
})

async function loadReport() {
  loading.value = true
  try {
    report.value = await reportApi.monthly(yearMonth.value)
  } catch (error) {
    report.value = null
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const data = await reportApi.list(12)
    history.value = Array.isArray(data) ? data : data ? [data] : []
  } catch (error) {
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

async function generateReport() {
  generating.value = true
  try {
    report.value = await reportApi.generate(yearMonth.value)
    ElMessage.success('报告已重新生成')
    loadHistory()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    generating.value = false
  }
}

function selectMonth(value) {
  if (!value || value === yearMonth.value) return
  yearMonth.value = value
}

watch(yearMonth, loadReport)

onMounted(() => {
  loadReport()
  loadHistory()
})
</script>

<template>
  <div class="page page-stack">
    <section class="card report-toolbar">
      <div class="report-toolbar__group">
        <span class="text-sm text-muted">报告月份</span>
        <el-date-picker
          v-model="yearMonth"
          type="month"
          value-format="YYYY-MM"
          :clearable="false"
          style="width: 150px"
        />
        <span class="text-sm text-muted">{{ formatMonthLabel(yearMonth) }} 消费分析报告</span>
      </div>
      <div class="report-toolbar__actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadReport">刷新</el-button>
        <el-button type="primary" :icon="Document" :loading="generating" @click="generateReport">
          重新生成
        </el-button>
      </div>
    </section>

    <div class="report-layout">
      <section v-loading="loading" class="card report-card">
        <template v-if="report">
          <header class="report-card__head">
            <div>
              <h2 class="report-card__title">
                {{ formatMonthLabel(report.yearMonth) }} 消费分析报告
              </h2>
              <p class="report-card__meta">
                生成时间 {{ formatDateTime(report.createdAt) }}
                <el-tag size="small" effect="plain" type="info" class="report-card__tag">
                  {{ aiEngineLabel(report.generator) }}
                </el-tag>
                <el-tag v-if="report.topCategory" size="small" effect="plain" type="warning">
                  {{ report.topCategory }}
                </el-tag>
              </p>
            </div>
          </header>

          <div class="report-stats">
            <div v-for="item in stats" :key="item.label" class="report-stats__item">
              <span class="text-xs text-muted">{{ item.label }}</span>
              <strong class="amount" :class="`text-${item.tone}`">{{ item.value }}</strong>
            </div>
          </div>

          <article class="md-body" v-html="contentHtml" />
        </template>

        <EmptyState
          v-else-if="!loading"
          icon="Document"
          title="该月还没有报告"
          description="点击「重新生成」让 AI 分析这个月的消费情况"
        >
          <el-button type="primary" :loading="generating" @click="generateReport">
            生成报告
          </el-button>
        </EmptyState>
      </section>

      <aside class="card report-history">
        <header class="card__head">
          <div>
            <h2 class="card__title">历史报告</h2>
            <p class="card__subtitle">最近 12 期</p>
          </div>
        </header>

        <div v-loading="historyLoading" class="report-history__list">
          <button
            v-for="item in history"
            :key="item.id || item.yearMonth"
            type="button"
            class="report-history__item"
            :class="{ 'is-active': item.yearMonth === yearMonth }"
            @click="selectMonth(item.yearMonth)"
          >
            <span class="report-history__month">{{ formatMonthLabel(item.yearMonth) }}</span>
            <span class="report-history__meta">
              支出 {{ formatMoney(item.totalExpense) }}
            </span>
            <span class="report-history__time">{{ formatDateTime(item.createdAt) }}</span>
          </button>

          <EmptyState
            v-if="!historyLoading && !history.length"
            compact
            icon="Files"
            title="暂无历史报告"
            description="生成报告后会保存在这里"
          />
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.report-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.report-toolbar__group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.report-toolbar__actions {
  display: flex;
  gap: 8px;
}

.report-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 18px;
  align-items: start;
}

.report-card {
  min-height: 320px;
}

.report-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--jz-border);
}

.report-card__title {
  font-size: 18px;
  font-weight: 600;
  color: var(--jz-text);
  letter-spacing: 0.4px;
}

.report-card__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--jz-text-muted);
}

.report-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 6px;
}

.report-stats__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 14px;
  border-radius: var(--jz-radius);
  background: #f9fbff;
  border: 1px solid #eef1fb;
}

.report-stats__item strong {
  font-size: 16px;
  color: var(--jz-text);
}

.md-body {
  margin-top: 16px;
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--jz-text-secondary);
}

.md-body :deep(h2) {
  margin: 20px 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--jz-text);
}

.md-body :deep(h3) {
  margin: 18px 0 8px;
  padding-left: 10px;
  border-left: 3px solid var(--jz-primary);
  font-size: 14.5px;
  font-weight: 600;
  color: var(--jz-text);
}

.md-body :deep(h4) {
  margin: 14px 0 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--jz-text);
}

.md-body :deep(p) {
  margin: 6px 0;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 6px 0 6px 4px;
  padding-left: 18px;
}

.md-body :deep(li) {
  margin: 4px 0;
}

.md-body :deep(strong) {
  color: var(--jz-text);
  font-weight: 600;
}

.md-body :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 14px;
  border-left: 3px solid #dfe5f7;
  border-radius: 4px;
  background: #f8faff;
  color: var(--jz-text-muted);
}

.md-body :deep(hr) {
  margin: 16px 0;
  border: none;
  border-top: 1px dashed var(--jz-border-strong);
}

.md-body :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: #f2f5fd;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12.5px;
  color: var(--jz-primary);
}

.report-history__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 80px;
}

.report-history__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-radius: var(--jz-radius-sm);
  border: 1px solid var(--jz-border);
  background: #fcfdff;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.report-history__item:hover {
  border-color: rgba(79, 110, 247, 0.4);
  background: #f8faff;
}

.report-history__item.is-active {
  border-color: rgba(79, 110, 247, 0.5);
  background: var(--jz-primary-soft);
}

.report-history__month {
  font-size: 13px;
  font-weight: 600;
  color: var(--jz-text);
}

.report-history__meta {
  font-size: 12px;
  color: var(--jz-text-secondary);
  font-variant-numeric: tabular-nums;
}

.report-history__time {
  font-size: 11px;
  color: var(--jz-text-muted);
}

@media (max-width: 1280px) {
  .report-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
