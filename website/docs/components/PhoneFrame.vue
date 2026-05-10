<template>
  <div class="phone-frame">
    <slot />
  </div>
</template>

<style scoped>
.phone-frame {
  position: relative;
  border-radius: 36px;
  overflow: hidden;
  border: 6px solid #1a1a2e;
  box-shadow:
    0 25px 60px -12px rgba(0,0,0,0.5),
    inset 0 0 0 2px rgba(255,255,255,0.05);
  flex-shrink: 0;
}
</style>
