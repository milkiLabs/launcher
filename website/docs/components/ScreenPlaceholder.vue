<script setup>
defineProps({
  label: { type: String, default: 'Screenshot coming soon' },
  aspect: { type: String, default: 'phone' }, // 'phone' | 'wide'
  icon: { type: String, default: '📱' }
})
</script>

<template>
  <div class="screen-placeholder" :class="aspect">
    <div class="placeholder-inner">
      <span class="placeholder-icon">{{ icon }}</span>
      <span class="placeholder-label">{{ label }}</span>
    </div>
  </div>
</template>

<style scoped>
.screen-placeholder {
  width: 100%;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(34,197,94,0.06) 0%, rgba(34,197,94,0.02) 100%);
  border: 2px dashed rgba(34,197,94,0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.screen-placeholder.phone {
  aspect-ratio: 9/19.5;
  max-width: 280px;
}

.screen-placeholder.wide {
  aspect-ratio: 16/10;
}

.placeholder-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 2rem;
  text-align: center;
}

.placeholder-icon {
  font-size: 2.5rem;
  opacity: 0.6;
}

.placeholder-label {
  font-family: 'DM Sans', sans-serif;
  font-size: 0.82rem;
  font-weight: 500;
  color: var(--vp-c-text-3);
  max-width: 160px;
  line-height: 1.4;
}
</style>
