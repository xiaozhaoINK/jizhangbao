<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  flat: { type: Boolean, default: false }
})
</script>

<template>
  <section class="card chart-card" :class="{ 'card--flat': flat }">
    <header class="card__head">
      <div class="chart-card__heading">
        <h2 class="card__title">
          {{ title }}
          <slot name="badge" />
        </h2>
        <p v-if="subtitle" class="card__subtitle">{{ subtitle }}</p>
      </div>
      <div class="card__extra no-print">
        <slot name="extra" />
      </div>
    </header>
    <slot />
    <footer v-if="$slots.footer" class="chart-card__footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<style scoped>
.chart-card__heading {
  min-width: 0;
}

.chart-card__footer {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--jz-border);
}
</style>
