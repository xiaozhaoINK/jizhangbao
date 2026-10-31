<script setup>
import { computed } from 'vue'
import { CaretBottom, CaretTop, Minus } from '@element-plus/icons-vue'
import { toNumber } from '@/utils/format'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '—' },
  icon: { type: String, default: 'TrendCharts' },
  tone: { type: String, default: 'primary' },
  delta: { type: [Number, String], default: null },
  deltaInverse: { type: Boolean, default: false },
  deltaLabel: { type: String, default: '较上月' },
  hint: { type: String, default: '' }
})

const hasDelta = computed(() => props.delta !== null && props.delta !== undefined && props.delta !== '')

const deltaValue = computed(() => toNumber(props.delta))

const isFlat = computed(() => Math.abs(deltaValue.value) < 0.005)

const deltaTone = computed(() => {
  if (isFlat.value) return 'flat'
  const rising = deltaValue.value > 0
  const good = props.deltaInverse ? !rising : rising
  return good ? 'good' : 'bad'
})

const deltaIcon = computed(() => {
  if (isFlat.value) return Minus
  return deltaValue.value > 0 ? CaretTop : CaretBottom
})

const deltaText = computed(() => {
  if (isFlat.value) return '持平'
  const abs = Math.abs(deltaValue.value).toFixed(1)
  return `${deltaValue.value > 0 ? '+' : '-'}${abs}%`
})
</script>

<template>
  <article class="stat-card" :class="`stat-card--${tone}`">
    <header class="stat-card__head">
      <span class="stat-card__label">{{ label }}</span>
      <span class="stat-card__icon">
        <el-icon :size="17"><component :is="icon" /></el-icon>
      </span>
    </header>
    <p class="stat-card__value amount">{{ value }}</p>
    <footer class="stat-card__foot">
      <span v-if="hasDelta" class="stat-card__delta" :class="`stat-card__delta--${deltaTone}`">
        <el-icon :size="12"><component :is="deltaIcon" /></el-icon>
        {{ deltaText }}
      </span>
      <span v-if="hasDelta" class="stat-card__delta-label">{{ deltaLabel }}</span>
      <span v-if="hint" class="stat-card__hint">{{ hint }}</span>
    </footer>
  </article>
</template>

<style scoped>
.stat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 20px 16px;
  background: var(--jz-card);
  border: 1px solid var(--jz-border);
  border-radius: var(--jz-radius-lg);
  box-shadow: var(--jz-shadow);
  overflow: hidden;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
  --tone: var(--jz-primary);
  --tone-soft: rgba(79, 110, 247, 0.1);
}

.stat-card::after {
  content: '';
  position: absolute;
  top: -40px;
  right: -40px;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: var(--tone-soft);
  opacity: 0.55;
  pointer-events: none;
}

.stat-card:hover {
  box-shadow: var(--jz-shadow-hover);
  transform: translateY(-1px);
}

.stat-card--income {
  --tone: var(--jz-income);
  --tone-soft: var(--jz-income-soft);
}

.stat-card--expense {
  --tone: var(--jz-expense);
  --tone-soft: var(--jz-expense-soft);
}

.stat-card--warning {
  --tone: var(--jz-warning);
  --tone-soft: var(--jz-warning-soft);
}

.stat-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.stat-card__label {
  font-size: 13px;
  color: var(--jz-text-secondary);
  letter-spacing: 0.2px;
}

.stat-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: var(--tone);
  background: var(--tone-soft);
}

.stat-card__value {
  position: relative;
  z-index: 1;
  font-size: 25px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--jz-text);
  letter-spacing: 0.4px;
}

.stat-card__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 18px;
  font-size: 12px;
  color: var(--jz-text-muted);
  position: relative;
  z-index: 1;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.stat-card__delta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 6px 1px 3px;
  border-radius: 6px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.stat-card__delta--good {
  color: var(--jz-income);
  background: var(--jz-income-soft);
}

.stat-card__delta--bad {
  color: var(--jz-expense);
  background: var(--jz-expense-soft);
}

.stat-card__delta--flat {
  color: var(--jz-text-muted);
  background: #f2f5fb;
}

.stat-card__delta-label {
  color: var(--jz-text-muted);
}

.stat-card__hint {
  color: var(--jz-text-muted);
}
</style>
