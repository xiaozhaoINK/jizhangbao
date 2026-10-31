<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Promotion, Refresh } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import { aiApi, billApi, categoryApi } from '@/api'
import { PAY_METHODS } from '@/config/navigation'
import { currentYearMonth, formatMoney, formatMonthLabel, formatPercent, todayString, toNumber } from '@/utils/format'
import { aiEngineLabel } from '@/utils/labels'

const aiStatus = ref(null)
const statusLoading = ref(false)

const text = ref('')
const parsing = ref(false)
const recording = ref(false)
const saving = ref(false)
const parsed = ref(null)
const expenseTree = ref([])
const incomeTree = ref([])

const analysis = ref(null)
const analysisLoading = ref(false)
const insightMonth = ref(currentYearMonth())

const examples = ['昨天午饭花了 32 元', '打车 28.5 元，微信支付', '工资到账 16800', '周末看电影 80 元']

const cascaderProps = {
  value: 'id',
  label: 'name',
  children: 'children',
  emitPath: false,
  checkStrictly: true,
  expandTrigger: 'hover'
}

const isLlm = computed(() => aiStatus.value?.mode === 'llm')

const statusLabel = computed(() => (isLlm.value ? '大模型模式' : '规则引擎模式'))

const categoryOptions = computed(() =>
  parsed.value?.type === 'INCOME' ? incomeTree.value : expenseTree.value
)

const confidencePercent = computed(() =>
  Math.round(toNumber(parsed.value?.confidence) * 100)
)

const insightGroups = computed(() => [
  { key: 'insights', title: '消费洞察', items: analysis.value?.insights || [] },
  { key: 'suggestions', title: '省钱建议', items: analysis.value?.suggestions || [] }
])

const insightTagType = {
  INFO: 'info',
  WARNING: 'warning',
  GOOD: 'success',
  TIP: 'primary'
}

function typeTag(type) {
  return insightTagType[type] || 'info'
}

function typeLabel(type) {
  return { INFO: '提示', WARNING: '预警', GOOD: '良好', TIP: '建议' }[type] || '洞察'
}

async function loadStatus() {
  statusLoading.value = true
  try {
    aiStatus.value = await aiApi.status()
  } catch (error) {
    aiStatus.value = null
  } finally {
    statusLoading.value = false
  }
}

async function loadCategories() {
  try {
    const [expense, income] = await Promise.all([
      categoryApi.tree('EXPENSE'),
      categoryApi.tree('INCOME')
    ])
    expenseTree.value = expense || []
    incomeTree.value = income || []
  } catch (error) {
    expenseTree.value = []
    incomeTree.value = []
  }
}

async function loadAnalysis() {
  analysisLoading.value = true
  try {
    analysis.value = await aiApi.analysis(insightMonth.value)
  } catch (error) {
    analysis.value = null
  } finally {
    analysisLoading.value = false
  }
}

function useExample(value) {
  text.value = value
}

async function parseText() {
  const content = text.value.trim()
  if (!content) {
    ElMessage.warning('请先输入一句记账描述')
    return
  }
  parsing.value = true
  parsed.value = null
  try {
    const data = await aiApi.parse(content)
    parsed.value = {
      ...data,
      type: data?.type === 'INCOME' ? 'INCOME' : 'EXPENSE',
      amount: toNumber(data?.amount) || null,
      billDate: data?.billDate || todayString(),
      payMethod: data?.payMethod || '微信',
      remark: data?.remark || content
    }
  } catch (error) {
    parsed.value = null
  } finally {
    parsing.value = false
  }
}

async function confirmRecord() {
  const current = parsed.value
  if (!current) return
  if (toNumber(current.amount) <= 0) {
    ElMessage.warning('请填写有效的金额')
    return
  }
  if (!current.categoryId) {
    ElMessage.warning('请选择分类')
    return
  }

  saving.value = true
  try {
    await billApi.create({
      type: current.type,
      categoryId: current.categoryId,
      amount: Number(toNumber(current.amount).toFixed(2)),
      billDate: current.billDate,
      payMethod: current.payMethod || null,
      remark: current.remark || null
    })
    ElMessage.success('已记账成功')
    parsed.value = null
    text.value = ''
    loadAnalysis()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function recordDirect() {
  const content = text.value.trim()
  if (!content) {
    ElMessage.warning('请先输入一句记账描述')
    return
  }
  recording.value = true
  try {
    const bill = await aiApi.record(content)
    ElMessage.success(
      `已记账：${bill?.categoryName || '未分类'} ${formatMoney(bill?.amount)}`
    )
    text.value = ''
    parsed.value = null
    loadAnalysis()
  } catch (error) {
    // 错误提示已在响应拦截器统一处理
  } finally {
    recording.value = false
  }
}

watch(insightMonth, loadAnalysis)

onMounted(() => {
  loadStatus()
  loadCategories()
  loadAnalysis()
})
</script>

<template>
  <div class="page page-stack">
    <section v-loading="statusLoading" class="card ai-status">
      <div class="ai-status__main">
        <span class="ai-status__badge" :class="{ 'ai-status__badge--rule': !isLlm }">
          <el-icon :size="16"><MagicStick /></el-icon>
          {{ statusLabel }}
        </span>
        <div class="ai-status__meta">
          <p class="text-strong">{{ aiStatus?.engine || '记账宝智能引擎' }}</p>
          <p class="text-sm text-muted">
            {{ aiStatus?.description || '支持自然语言记账与消费洞察分析' }}
          </p>
        </div>
      </div>
      <div class="ai-status__facts">
        <div class="ai-status__fact">
          <span class="text-xs text-muted">引擎模型</span>
          <strong>{{ aiStatus?.model || '—' }}</strong>
        </div>
        <div class="ai-status__fact">
          <span class="text-xs text-muted">服务提供方</span>
          <strong>{{ aiStatus?.provider || '本地' }}</strong>
        </div>
        <div class="ai-status__fact">
          <span class="text-xs text-muted">每日调用上限</span>
          <strong>{{ aiStatus?.dailyLimit ?? '—' }} 次</strong>
        </div>
      </div>
    </section>

    <div class="ai-layout">
      <div class="stack-12">
        <section class="card">
          <header class="card__head">
            <div>
              <h2 class="card__title">自然语言记账</h2>
              <p class="card__subtitle">用一句话描述这笔消费，自动识别金额、分类与日期</p>
            </div>
          </header>

          <el-input
            v-model="text"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            resize="none"
            placeholder="例如：昨天午饭花了 32 元，微信支付"
            @keyup.ctrl.enter="parseText"
          />

          <div class="ai-examples">
            <span class="text-xs text-muted">示例</span>
            <el-tag
              v-for="item in examples"
              :key="item"
              class="ai-examples__tag"
              size="small"
              effect="plain"
              @click="useExample(item)"
            >
              {{ item }}
            </el-tag>
          </div>

          <div class="ai-actions">
            <el-button
              type="primary"
              :icon="MagicStick"
              :loading="parsing"
              @click="parseText"
            >
              解析这句话
            </el-button>
            <el-button :icon="Promotion" :loading="recording" @click="recordDirect">
              直接记账
            </el-button>
            <span class="text-xs text-muted">解析后可修改字段再确认落账</span>
          </div>
        </section>

        <section v-if="parsed" class="card">
          <header class="card__head">
            <div>
              <h2 class="card__title">
                解析结果
                <el-tag size="small" effect="light" :type="parsed.type === 'INCOME' ? 'success' : 'danger'">
                  {{ parsed.typeLabel || (parsed.type === 'INCOME' ? '收入' : '支出') }}
                </el-tag>
                <el-tag size="small" effect="plain" type="info">
                  {{ aiEngineLabel(parsed.engine, '规则引擎') }}
                </el-tag>
              </h2>
              <p class="card__subtitle">
                置信度 {{ confidencePercent }}% · 原始输入「{{ parsed.rawText || text }}」
              </p>
            </div>
            <el-button text type="primary" @click="parsed = null">关闭</el-button>
          </header>

          <el-alert
            v-if="!parsed.complete && parsed.hints?.length"
            class="ai-hints"
            type="warning"
            :closable="false"
            show-icon
            title="还需要你补充确认"
          >
            <ul class="ai-hints__list">
              <li v-for="(hint, index) in parsed.hints" :key="index">{{ hint }}</li>
            </ul>
          </el-alert>

          <el-form label-width="84px" class="ai-form">
            <el-form-item label="金额">
              <el-input-number
                v-model="parsed.amount"
                :min="0.01"
                :precision="2"
                :step="10"
                :controls-position="'right'"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="分类">
              <el-cascader
                v-model="parsed.categoryId"
                :options="categoryOptions"
                :props="cascaderProps"
                placeholder="请选择分类"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker
                v-model="parsed.billDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="支付方式">
              <el-select v-model="parsed.payMethod" clearable style="width: 100%">
                <el-option
                  v-for="method in PAY_METHODS"
                  :key="method"
                  :label="method"
                  :value="method"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="parsed.remark" maxlength="255" />
            </el-form-item>
          </el-form>

          <div class="ai-actions ai-actions--end">
            <el-button @click="parsed = null">取消</el-button>
            <el-button type="primary" :loading="saving" @click="confirmRecord">
              确认落账
            </el-button>
          </div>
        </section>

        <section v-else class="card">
          <EmptyState
            icon="ChatDotSquare"
            title="还没有解析结果"
            description="在上方输入一句话，或直接点击「直接记账」由 AI 完成识别与落账"
          />
        </section>

        <section class="card">
          <header class="card__head">
            <div>
              <h2 class="card__title">记账小贴士</h2>
              <p class="card__subtitle">这样描述，识别会更准确</p>
            </div>
          </header>
          <ul class="ai-tips">
            <li><span class="ai-tips__dot" />支持相对日期：昨天、上周五、这个月 3 号</li>
            <li><span class="ai-tips__dot" />金额可以带单位：午饭 32 元、打车 28.5</li>
            <li><span class="ai-tips__dot" />说清支付方式：微信、支付宝、银行卡</li>
            <li><span class="ai-tips__dot" />解析结果可修改金额、分类与日期后再落账</li>
          </ul>
        </section>
      </div>

      <section v-loading="analysisLoading" class="card ai-insight">
        <header class="card__head">
          <div>
            <h2 class="card__title">本月消费洞察</h2>
            <p class="card__subtitle">
              {{ formatMonthLabel(insightMonth) }} · 由
              {{ aiEngineLabel(analysis?.generator || aiStatus?.engine) }} 生成
            </p>
          </div>
          <div class="card__extra">
            <el-date-picker
              v-model="insightMonth"
              type="month"
              value-format="YYYY-MM"
              :clearable="false"
              size="small"
              style="width: 120px"
            />
            <el-button :icon="Refresh" size="small" :loading="analysisLoading" @click="loadAnalysis">
              刷新
            </el-button>
          </div>
        </header>

        <template v-if="analysis">
          <div class="ai-insight__summary">
            <div class="ai-insight__title">{{ analysis.title }}</div>
            <p class="ai-insight__text">{{ analysis.summary }}</p>
            <div class="ai-insight__numbers">
              <div>
                <span class="text-xs text-muted">收入</span>
                <strong class="amount text-income">{{ formatMoney(analysis.totalIncome) }}</strong>
              </div>
              <div>
                <span class="text-xs text-muted">支出</span>
                <strong class="amount text-expense">{{ formatMoney(analysis.totalExpense) }}</strong>
              </div>
              <div>
                <span class="text-xs text-muted">结余</span>
                <strong class="amount">{{ formatMoney(analysis.balance) }}</strong>
              </div>
              <div>
                <span class="text-xs text-muted">笔数</span>
                <strong class="amount">{{ toNumber(analysis.billCount) }}</strong>
              </div>
            </div>
          </div>

          <div v-for="group in insightGroups" :key="group.key" class="ai-insight__group">
            <p class="section-title">{{ group.title }}</p>
            <div v-if="group.items.length" class="stack-8">
              <article v-for="(item, index) in group.items" :key="index" class="insight-item">
                <div class="insight-item__head">
                  <el-tag size="small" effect="light" :type="typeTag(item.type)">
                    {{ typeLabel(item.type) }}
                  </el-tag>
                  <strong>{{ item.title }}</strong>
                </div>
                <p class="insight-item__detail">{{ item.detail }}</p>
                <p v-if="item.evidence" class="insight-item__evidence">{{ item.evidence }}</p>
              </article>
            </div>
            <p v-else class="text-sm text-muted">本期暂无{{ group.title }}。</p>
          </div>

          <p class="ai-insight__foot">
            支出占收入比例
            {{ formatPercent(toNumber(analysis.totalIncome) > 0 ? (toNumber(analysis.totalExpense) / toNumber(analysis.totalIncome)) * 100 : 0) }}
          </p>
        </template>

        <EmptyState
          v-else-if="!analysisLoading"
          icon="DataAnalysis"
          title="暂无消费洞察"
          description="本月记录几笔账单后，AI 会生成消费结构与省钱建议"
        >
          <el-button type="primary" @click="loadAnalysis">重新生成</el-button>
        </EmptyState>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ai-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.ai-status__main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.ai-status__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #ffffff;
  background: var(--jz-gradient);
  box-shadow: 0 6px 16px rgba(79, 110, 247, 0.28);
  flex-shrink: 0;
}

.ai-status__badge--rule {
  background: linear-gradient(135deg, #ff9f0a, #ff7a45);
  box-shadow: 0 6px 16px rgba(255, 159, 10, 0.28);
}

.ai-status__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ai-status__facts {
  display: flex;
  gap: 28px;
}

.ai-status__fact {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-status__fact strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--jz-text);
}

.ai-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.ai-examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.ai-examples__tag {
  cursor: pointer;
  transition: color 0.16s ease, border-color 0.16s ease;
}

.ai-examples__tag:hover {
  color: var(--jz-primary);
  border-color: var(--jz-primary);
}

.ai-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.ai-actions--end {
  justify-content: flex-end;
  margin-top: 8px;
}

.ai-tips {
  display: flex;
  flex-direction: column;
  gap: 8px;
  list-style: none;
  font-size: 12.5px;
  color: var(--jz-text-secondary);
}

.ai-tips li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-tips__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--jz-primary);
  opacity: 0.6;
  flex-shrink: 0;
}

.ai-hints {
  margin-bottom: 14px;
  border-radius: var(--jz-radius);
}

.ai-hints__list {
  margin: 4px 0 0;
  padding-left: 16px;
  font-size: 12.5px;
  line-height: 1.7;
}

.ai-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.ai-insight__summary {
  padding: 14px 16px;
  border-radius: var(--jz-radius);
  background: linear-gradient(150deg, #f5f7ff, #fbfcff);
  border: 1px solid #eef1fb;
}

.ai-insight__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--jz-text);
}

.ai-insight__text {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--jz-text-secondary);
}

.ai-insight__numbers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #e4e9f7;
}

.ai-insight__numbers > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-insight__numbers strong {
  font-size: 14px;
  color: var(--jz-text);
}

.ai-insight__group {
  margin-top: 18px;
}

.ai-insight__group .section-title {
  margin-bottom: 10px;
}

.insight-item {
  padding: 12px 14px;
  border-radius: var(--jz-radius);
  border: 1px solid var(--jz-border);
  background: #fcfdff;
}

.insight-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.insight-item__head strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--jz-text);
}

.insight-item__detail {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--jz-text-secondary);
}

.insight-item__evidence {
  margin-top: 4px;
  font-size: 12px;
  color: var(--jz-text-muted);
}

.ai-insight__foot {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px dashed var(--jz-border);
  font-size: 12px;
  color: var(--jz-text-muted);
}

@media (max-width: 1280px) {
  .ai-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .ai-status__facts {
    gap: 18px;
  }
}
</style>
