<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import { Loading } from '@element-plus/icons-vue'
import EmptyState from './EmptyState.vue'

const props = defineProps({
  option: { type: Object, default: null },
  height: { type: String, default: '320px' },
  loading: { type: Boolean, default: false },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' }
})

const container = ref(null)
const instance = shallowRef(null)
let observer = null

function render() {
  if (!container.value || !props.option) return
  if (!instance.value) {
    instance.value = echarts.init(container.value)
  }
  instance.value.setOption(props.option, true)
  instance.value.resize()
}

function resize() {
  instance.value?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
  if (typeof ResizeObserver !== 'undefined' && container.value) {
    observer = new ResizeObserver(() => resize())
    observer.observe(container.value)
  }
})

watch(
  () => props.option,
  () => nextTick(render)
)

watch(
  () => props.loading,
  (value) => {
    if (!value) nextTick(render)
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  observer?.disconnect()
  observer = null
  instance.value?.dispose()
  instance.value = null
})

defineExpose({ resize })
</script>

<template>
  <div class="base-chart" :style="{ height }">
    <div ref="container" class="base-chart__canvas" />
    <div v-if="loading" class="base-chart__mask">
      <el-icon class="is-loading" :size="22"><Loading /></el-icon>
    </div>
    <div v-else-if="empty" class="base-chart__mask base-chart__mask--empty">
      <EmptyState :title="emptyText" description="调整筛选条件后再看看吧" icon="PieChart" />
    </div>
  </div>
</template>

<style scoped>
.base-chart {
  position: relative;
  width: 100%;
}

.base-chart__canvas {
  width: 100%;
  height: 100%;
}

.base-chart__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.72);
  color: var(--jz-primary);
  border-radius: var(--jz-radius);
}

.base-chart__mask--empty {
  background: #ffffff;
}
</style>
