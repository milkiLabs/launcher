<template>
  <video :src="src" autoplay muted controls style="max-width: 600px; max-height: 500px; object-fit: contain;" />
</template>

<script setup lang="ts">
defineProps<{ src: string }>()
</script>
