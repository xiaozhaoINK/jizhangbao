<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { adminApi } from '@/api'
import { formatMoney, formatNumber, formatPercent, toNumber } from '@/utils/format'
import { aiEngineLabel, aiSceneLabel } from '@/utils/labels'

const loading = ref(true)
const usageLoading = ref(false)
const overview = ref(null)
const usage = ref([])

const cards = computed(() => {
  const data = overview.value || {}
  return [
    { label: '用户总数', value: formatNumber(data.userCount, 0), icon: 'User', tone: 'primary', hint: `今日新增 ${toNumber(data.todayNewUsers)} 人` },
    { label: '活跃用户', value: formatNumber(data.activeUserCount, 0), icon: 'UserFilled', tone: 'income', hint: '近 30 天有记账行为' },
    { label: '账单总数', value: formatNumber(data.billCount, 0), icon: 'Tickets', tone: 'primary', hint: '全站累计记账笔数' },
    { label: '分类总数', value: formatNumber(data.categoryCount, 0), icon: 'Collection', tone: 'primary', hint: '所有用户的分类合计' },
    { label: '累计收入', value: formatMoney(data.totalIncome), icon: 'Money', tone: 'income', hint: '全站收入金额' },
    { label: '累计支出', value: formatMoney(data.totalExpense), icon: 'Wallet', tone: 'expense', hint: '全站支出金额' },
    { label: '月度报告数', value: formatNumber(data.reportCount, 0), icon: 'Document', tone: 'primary', hint: '已生成的消费报告' },
    { label: 'AI 调用总数', value: formatNumber(data.aiCallCount, 0), icon: 'MagicStick', tone: 'warning', hint: `今日 ${toNumber(data.todayAiCallCount)} 次` }
  ]
})

const usageRows = computed(() =>
  usage.value.map((item) => ({
    scene: item.scene || '未分类',
    engine: item.engine || '—',
    cnt: toNumber(item.cnt)
  }))
)

const usageTotal = computed(() => usageRows.value.reduce((sum, item) => sum + item.cnt, 0))

function usagePercent(row) {
  return usageTotal.value > 0 ? (row.cnt / usageTotal.value) * 100 : 0
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await adminApi.overview()
  } catch (error) {
    overview.value = null
  } finally {
    loading.value = false
  }
}

async function loadUsage() {
  usageLoading.value = true
  try {
    const data = await adminApi.aiUsage()
    usage.value = Array.isArray(data) ? data : []
  } catch (error) {
    usage.value = []
  } finally {
    usageLoading.value = false
  }
}

function reload() {
  loadOverview()
  loadUsage()
}

onMounted(reload)
</script>

<template>
  <div class="page page-stack">
    <section class="card overview-toolbar">
      <div>
        <h2 class="card__title">平台运行概览</h2>
        <p class="card__subtitle">汇总全站用户、账单与 AI 能力使用情况</p>
      </div>
      <el-button :icon="Refresh" :loading="loading || usageLoading" @click="reload">刷新数据</el-button>
    </section>

    <div v-loading="loading" class="grid grid-4">
      <StatCard v-for="card in cards" :key="card.label" v-bind="card" />
    </div>

    <section class="card">
      <header class="card__head">
        <div>
          <h2 class="card__title">AI 调用分布</h2>
          <p class="card__subtitle">按场景与引擎统计调用次数，共 {{ usageTotal }} 次</p>
        </div>
      </header>

      <el-table v-loading="usageLoading" :data="usageRows" row-key="scene">
        <el-table-column label="调用场景" min-width="160">
          <template #default="{ row }">
            <span class="text-strong">{{ aiSceneLabel(row.scene) }}</span>
            <span class="scene-code text-xs text-muted mono">{{ row.scene }}</span>
          </template>
        </el-table-column>
        <el-table-column label="引擎" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="row.engine === 'LLM' ? 'primary' : 'info'">
              {{ aiEngineLabel(row.engine, '—') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="调用次数" width="130" align="right">
          <template #default="{ row }">
            <span class="num amount">{{ formatNumber(row.cnt, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="占比" min-width="200">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress
                :percentage="Number(usagePercent(row).toFixed(1))"
                :stroke-width="6"
                :show-text="false"
                color="#4F6EF7"
              />
              <span class="text-sm num text-muted">{{ formatPercent(usagePercent(row), 1) }}</span>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            compact
            icon="DataLine"
            title="暂无 AI 调用记录"
            description="当用户使用智能解析或问答后，这里会展示调用分布"
          />
        </template>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.overview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.usage-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scene-code {
  margin-left: 8px;
  letter-spacing: 0.4px;
}

.usage-cell :deep(.el-progress) {
  flex: 1;
  min-width: 80px;
}
</style>
