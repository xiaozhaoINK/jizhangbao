<template>
  <div ref="container" class="echart" :style="{ height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '220px' }
})

const container = ref(null)
let chart = null
let observer = null

const render = () => {
  if (!container.value) return
  if (!chart) {
    chart = echarts.init(container.value, null, { renderer: 'canvas' })
  }
  chart.setOption(props.option, true)
  chart.resize()
}

onMounted(async () => {
  await nextTick()
  render()
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => chart && chart.resize())
    observer.observe(container.value)
  }
})

watch(
  () => props.option,
  async () => {
    await nextTick()
    render()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.echart {
  width: 100%;
}
</style>
