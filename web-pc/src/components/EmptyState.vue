<script setup>
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  icon: { type: String, default: 'DocumentRemove' },
  compact: { type: Boolean, default: false }
})
</script>

<template>
  <div class="empty-state" :class="{ 'empty-state--compact': compact }">
    <span class="empty-state__icon">
      <el-icon :size="24"><component :is="icon" /></el-icon>
    </span>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 36px 12px;
  text-align: center;
}

.empty-state--compact {
  padding: 20px 12px;
}

.empty-state__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  color: #a8b2c8;
  background: #f4f7fd;
  margin-bottom: 2px;
}

.empty-state__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--jz-text-secondary);
}

.empty-state__desc {
  font-size: 12.5px;
  color: var(--jz-text-muted);
  max-width: 320px;
}

.empty-state__action {
  margin-top: 6px;
}
</style>
