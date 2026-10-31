<template>
  <div class="bill-item" @click="$emit('click')">
    <div class="bill-item__icon" :style="{ background: iconBg }">
      <van-icon :name="iconName" :color="item.categoryColor || '#4f6ef7'" size="18" />
    </div>
    <div class="bill-item__main">
      <div class="bill-item__row">
        <span class="bill-item__name ellipsis">{{ item.categoryName || '未分类' }}</span>
        <span class="bill-item__amount num" :class="amountClass(item.type)">
          {{ amountSign(item.type) }}{{ money(item.amount) }}
        </span>
      </div>
      <div class="bill-item__row bill-item__row--sub">
        <span class="ellipsis">
          {{ item.remark || item.parentCategoryName || '无备注' }}
          <van-tag v-if="item.source === 'AI'" type="primary" plain size="mini" class="ml">AI</van-tag>
        </span>
        <span>{{ friendlyDate(item.billDate) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { amountClass, amountSign, friendlyDate, money } from '@/utils/format'

const props = defineProps({
  item: { type: Object, required: true }
})
defineEmits(['click'])

const ICON_MAP = {
  food: 'shop-o',
  transport: 'logistics',
  shopping: 'gift-o',
  home: 'wap-home-o',
  entertainment: 'music-o',
  medical: 'plus',
  education: 'records',
  gift: 'gift-card-o',
  salary: 'gold-coin-o',
  other: 'ellipsis',
  'other-income': 'ellipsis'
}

const iconName = computed(() => ICON_MAP[props.item.categoryIcon] || 'label-o')

const iconBg = computed(() => {
  const color = props.item.categoryColor || '#4f6ef7'
  return `${color}1a`
})
</script>

<style scoped>
.bill-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
}

.bill-item__icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bill-item__main {
  flex: 1;
  min-width: 0;
}

.bill-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.bill-item__row--sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8b95b3;
}

.bill-item__name {
  font-size: 15px;
  font-weight: 500;
}

.bill-item__amount {
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.ml {
  margin-left: 4px;
}

.text-income {
  color: #00b578;
}

.text-expense {
  color: #ff5b5b;
}
</style>
