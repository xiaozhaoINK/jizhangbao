<template>
  <div class="month-switch">
    <van-icon name="arrow-left" size="14" @click="shift(-1)" />
    <span class="month-switch__text num" @click="$emit('pick')">{{ label }}</span>
    <van-icon name="arrow" size="14" @click="shift(1)" />
    <van-tag v-if="isCurrent" type="primary" plain size="mini">本月</van-tag>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { currentMonth } from '@/utils/format'

const props = defineProps({
  modelValue: { type: String, required: true }
})
const emit = defineEmits(['update:modelValue', 'pick'])

const label = computed(() => {
  const [y, m] = props.modelValue.split('-')
  return `${y} 年 ${Number(m)} 月`
})

const isCurrent = computed(() => props.modelValue === currentMonth())

const shift = (offset) => {
  const [y, m] = props.modelValue.split('-').map(Number)
  const date = new Date(y, m - 1 + offset, 1)
  const next = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  emit('update:modelValue', next)
}
</script>

<style scoped>
.month-switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 0;
  color: #fff;
}

.month-switch__text {
  font-size: 15px;
  font-weight: 600;
  padding: 2px 6px;
}
</style>
