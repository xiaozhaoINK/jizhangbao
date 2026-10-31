<script setup>
import { computed } from 'vue'
import { hexToRgba } from '@/utils/format'
import { resolveCategoryIcon } from '@/utils/categoryIcons'

const props = defineProps({
  icon: { type: String, default: '' },
  name: { type: String, default: '' },
  color: { type: String, default: '#4F6EF7' },
  size: { type: Number, default: 30 }
})

const iconComponent = computed(() => resolveCategoryIcon(props.icon))
const initial = computed(() => (props.name || '').trim().charAt(0) || '记')

const chipStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: `${Math.round(props.size * 0.32)}px`,
  color: props.color,
  background: hexToRgba(props.color, 0.12)
}))
</script>

<template>
  <span class="cat-chip" :style="chipStyle">
    <el-icon v-if="iconComponent" :size="Math.round(size * 0.52)">
      <component :is="iconComponent" />
    </el-icon>
    <span v-else class="cat-chip__text" :style="{ fontSize: `${Math.round(size * 0.4)}px` }">
      {{ initial }}
    </span>
  </span>
</template>

<style scoped>
.cat-chip__text {
  font-weight: 600;
  line-height: 1;
}
</style>
